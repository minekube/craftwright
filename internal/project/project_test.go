package project_test

import (
	"bytes"
	"encoding/json"
	"io/fs"
	"os"
	"path/filepath"
	"testing"

	"github.com/minekube/craftwright/internal/project"
)

func TestInitCreatesConfigCacheAndArtifacts(t *testing.T) {
	dir := t.TempDir()
	layout := project.Layout{Root: dir}
	if err := project.Init(layout, false); err != nil {
		t.Fatal(err)
	}
	for _, rel := range []string{"craftwright.yaml", ".craftwright/cache", ".craftwright/artifacts"} {
		if _, err := os.Stat(filepath.Join(dir, rel)); err != nil {
			t.Fatalf("%s missing: %v", rel, err)
		}
	}
}

func TestInitWritesBackendDefaults(t *testing.T) {
	dir := t.TempDir()
	if err := project.Init(project.Layout{Root: dir}, false); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(filepath.Join(dir, "craftwright.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{
		"backend:",
		"type: memory",
		"headlessmcVersion: \"2.9.0\"",
		"specificsVersion: \"2.4.0\"",
	} {
		if !bytes.Contains(data, []byte(want)) {
			t.Fatalf("config missing %q:\n%s", want, data)
		}
	}
}

func TestPrepareCacheWritesMetadata(t *testing.T) {
	dir := t.TempDir()
	layout := project.Layout{Root: dir}
	if err := project.Init(layout, false); err != nil {
		t.Fatal(err)
	}
	record, err := project.PrepareCache(layout, project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric", Profile: "default"})
	if err != nil {
		t.Fatal(err)
	}
	if record.Minecraft != "1.21.6" || record.Loader != "fabric" || record.Profile != "default" {
		t.Fatalf("record = %#v", record)
	}
	if _, err := os.Stat(filepath.Join(dir, ".craftwright/cache/default/1.21.6-fabric.json")); err != nil {
		t.Fatalf("metadata missing: %v", err)
	}
}

func TestPrepareCacheRejectsUnsafeIdentifiers(t *testing.T) {
	tests := []struct {
		name string
		req  project.CacheRequest
	}{
		{
			name: "unsafe profile traversal",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric", Profile: "../../../escape"},
		},
		{
			name: "unsafe minecraft traversal",
			req:  project.CacheRequest{Minecraft: "../1.21.6", Loader: "fabric", Profile: "default"},
		},
		{
			name: "unsafe loader traversal",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric/../../x", Profile: "default"},
		},
		{
			name: "empty minecraft",
			req:  project.CacheRequest{Minecraft: "", Loader: "fabric", Profile: "default"},
		},
		{
			name: "empty loader",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: "", Profile: "default"},
		},
		{
			name: "dot profile",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric", Profile: "."},
		},
		{
			name: "dot minecraft",
			req:  project.CacheRequest{Minecraft: ".", Loader: "fabric", Profile: "default"},
		},
		{
			name: "dot loader",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: ".", Profile: "default"},
		},
		{
			name: "spaces",
			req:  project.CacheRequest{Minecraft: "1.21.6 beta", Loader: "fabric", Profile: "default"},
		},
		{
			name: "shell chars",
			req:  project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric;rm", Profile: "default"},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			base := t.TempDir()
			root := filepath.Join(base, "project")
			layout := project.Layout{Root: root}
			if err := project.Init(layout, false); err != nil {
				t.Fatal(err)
			}

			if _, err := project.PrepareCache(layout, tt.req); err == nil {
				t.Fatal("expected error")
			}
			assertNoCacheMetadataFiles(t, filepath.Join(root, ".craftwright", "cache"))
			if _, err := os.Stat(filepath.Join(base, "escape")); !os.IsNotExist(err) {
				t.Fatalf("escape path was written: %v", err)
			}
		})
	}
}

func TestInitValidatesExistingConfig(t *testing.T) {
	t.Run("valid config is not overwritten", func(t *testing.T) {
		dir := t.TempDir()
		configPath := filepath.Join(dir, "craftwright.yaml")
		validConfig := []byte(`version: 1

defaults:
  minecraft: "1.21.6"
  loader: fabric
  offline: true
  timeout: 2m
paths:
  artifacts: .craftwright/artifacts
  cache: .craftwright/cache
`)
		if err := os.WriteFile(configPath, validConfig, 0o644); err != nil {
			t.Fatal(err)
		}
		if err := project.Init(project.Layout{Root: dir}, false); err != nil {
			t.Fatal(err)
		}
		data, err := os.ReadFile(configPath)
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(data, validConfig) {
			t.Fatalf("config was overwritten:\n%s", data)
		}
	})

	t.Run("invalid config returns error", func(t *testing.T) {
		dir := t.TempDir()
		if err := os.WriteFile(filepath.Join(dir, "craftwright.yaml"), []byte("version: nope\n"), 0o644); err != nil {
			t.Fatal(err)
		}
		if err := project.Init(project.Layout{Root: dir}, false); err == nil {
			t.Fatal("expected error")
		}
	})

	t.Run("force overwrites invalid config", func(t *testing.T) {
		dir := t.TempDir()
		configPath := filepath.Join(dir, "craftwright.yaml")
		invalidConfig := []byte("version: nope\n")
		if err := os.WriteFile(configPath, invalidConfig, 0o644); err != nil {
			t.Fatal(err)
		}
		if err := project.Init(project.Layout{Root: dir}, true); err != nil {
			t.Fatal(err)
		}
		data, err := os.ReadFile(configPath)
		if err != nil {
			t.Fatal(err)
		}
		if bytes.Equal(data, invalidConfig) {
			t.Fatal("invalid config was not overwritten")
		}
	})
}

func TestPrepareCacheReusesExistingMatchingMetadata(t *testing.T) {
	dir := t.TempDir()
	layout := project.Layout{Root: dir}
	req := project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric", Profile: "default"}
	if err := project.Init(layout, false); err != nil {
		t.Fatal(err)
	}

	first, err := project.PrepareCache(layout, req)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, ".craftwright/cache/default/1.21.6-fabric.json")
	firstBytes, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}

	second, err := project.PrepareCache(layout, req)
	if err != nil {
		t.Fatal(err)
	}
	secondBytes, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}

	if !first.PreparedAt.Equal(second.PreparedAt) {
		t.Fatalf("prepared times differ: %s != %s", first.PreparedAt, second.PreparedAt)
	}
	if !bytes.Equal(firstBytes, secondBytes) {
		t.Fatalf("metadata changed:\nfirst: %s\nsecond: %s", firstBytes, secondBytes)
	}
}

func TestProjectTypesUseJSONFieldNames(t *testing.T) {
	layoutJSON, err := json.Marshal(project.Layout{Root: "/tmp/x"})
	if err != nil {
		t.Fatal(err)
	}
	if string(layoutJSON) != `{"root":"/tmp/x"}` {
		t.Fatalf("layout JSON = %s", layoutJSON)
	}

	requestJSON, err := json.Marshal(project.CacheRequest{Minecraft: "1.21.6", Loader: "fabric", Profile: "default"})
	if err != nil {
		t.Fatal(err)
	}
	if string(requestJSON) != `{"minecraft":"1.21.6","loader":"fabric","profile":"default"}` {
		t.Fatalf("request JSON = %s", requestJSON)
	}
}

func assertNoCacheMetadataFiles(t *testing.T, cacheDir string) {
	t.Helper()
	err := filepath.WalkDir(cacheDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.Type().IsRegular() {
			t.Fatalf("unexpected cache metadata file: %s", path)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}
