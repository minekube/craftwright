package config_test

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"github.com/minekube/craftwright/internal/config"
)

func TestDefaultsMatchMilestoneOne(t *testing.T) {
	cfg := config.Default()
	if cfg.Version != 1 {
		t.Fatalf("version = %d", cfg.Version)
	}
	if cfg.Defaults.Minecraft != "1.21.6" {
		t.Fatalf("minecraft = %q", cfg.Defaults.Minecraft)
	}
	if cfg.Defaults.Loader != "fabric" {
		t.Fatalf("loader = %q", cfg.Defaults.Loader)
	}
	if !cfg.Defaults.Offline {
		t.Fatalf("offline = false, want true")
	}
	if cfg.Defaults.Timeout != 2*time.Minute {
		t.Fatalf("timeout = %s", cfg.Defaults.Timeout)
	}
	if cfg.Defaults.RawTimeout.Duration != 2*time.Minute {
		t.Fatalf("raw timeout = %s", cfg.Defaults.RawTimeout.Duration)
	}
	if cfg.Defaults.RawTimeout.Set {
		t.Fatal("raw timeout set = true, want false")
	}
	if cfg.Paths.Artifacts != ".craftwright/artifacts" {
		t.Fatalf("artifacts = %q", cfg.Paths.Artifacts)
	}
	if cfg.Paths.Cache != ".craftwright/cache" {
		t.Fatalf("cache = %q", cfg.Paths.Cache)
	}
}

func TestDefaultIncludesHeadlessMCBackend(t *testing.T) {
	cfg := config.Default()
	if cfg.Backend.Type != "memory" {
		t.Fatalf("backend type = %q, want memory", cfg.Backend.Type)
	}
	if cfg.Backend.HeadlessMCVersion != "2.9.0" {
		t.Fatalf("headlessmc version = %q", cfg.Backend.HeadlessMCVersion)
	}
	if cfg.Backend.SpecificsVersion != "2.4.0" {
		t.Fatalf("specifics version = %q", cfg.Backend.SpecificsVersion)
	}
	if cfg.Backend.Java != "java" {
		t.Fatalf("java = %q, want java", cfg.Backend.Java)
	}
	if len(cfg.Backend.JVMArgs) != 2 {
		t.Fatalf("jvm args = %#v", cfg.Backend.JVMArgs)
	}
}

func TestLoadBackendConfig(t *testing.T) {
	path := writeConfig(t, `version: 1
defaults:
  minecraft: "1.21.6"
  loader: fabric
  offline: true
  timeout: 2m
backend:
  type: headlessmc
  headlessmcVersion: "2.9.0"
  specificsVersion: "2.4.0"
  java: "/usr/bin/java"
  jvmArgs:
    - "-Djava.awt.headless=true"
    - "-Xmx1G"
paths:
  artifacts: .craftwright/artifacts
  cache: .craftwright/cache
`)
	cfg, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Backend.Type != "headlessmc" || cfg.Backend.Java != "/usr/bin/java" {
		t.Fatalf("backend = %#v", cfg.Backend)
	}
	if got := strings.Join(cfg.Backend.JVMArgs, " "); got != "-Djava.awt.headless=true -Xmx1G" {
		t.Fatalf("jvm args = %q", got)
	}
}

func TestLoadProjectConfigOverridesDefaults(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	data := []byte("version: 1\n\ndefaults:\n  minecraft: \"1.20.4\"\n  loader: vanilla\n  offline: false\n  timeout: 30s\npaths:\n  artifacts: test-results/mcw\n  cache: .cache/mcw\n")
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	cfg, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Defaults.Minecraft != "1.20.4" {
		t.Fatalf("minecraft = %q", cfg.Defaults.Minecraft)
	}
	if cfg.Defaults.Loader != "vanilla" {
		t.Fatalf("loader = %q", cfg.Defaults.Loader)
	}
	if cfg.Defaults.Offline {
		t.Fatalf("offline = true, want false")
	}
	if cfg.Defaults.Timeout != 30*time.Second {
		t.Fatalf("timeout = %s", cfg.Defaults.Timeout)
	}
	if cfg.Paths.Artifacts != "test-results/mcw" {
		t.Fatalf("artifacts = %q", cfg.Paths.Artifacts)
	}
	if cfg.Paths.Cache != ".cache/mcw" {
		t.Fatalf("cache = %q", cfg.Paths.Cache)
	}
	if cfg.Backend.Type != "memory" {
		t.Fatalf("backend type = %q, want memory", cfg.Backend.Type)
	}
	if cfg.Backend.HeadlessMCVersion != "2.9.0" {
		t.Fatalf("backend headlessmc version = %q", cfg.Backend.HeadlessMCVersion)
	}
	if cfg.Backend.SpecificsVersion != "2.4.0" {
		t.Fatalf("backend specifics version = %q", cfg.Backend.SpecificsVersion)
	}
	if cfg.Backend.Java != "java" {
		t.Fatalf("backend java = %q", cfg.Backend.Java)
	}
}

func TestLoadProjectConfigAllowsExplicitZeroTimeout(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	data := []byte("version: 1\n\ndefaults:\n  timeout: 0s\n")
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}

	cfg, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Defaults.Timeout != 0 {
		t.Fatalf("timeout = %s, want 0s", cfg.Defaults.Timeout)
	}
	if cfg.Defaults.RawTimeout.Duration != 0 {
		t.Fatalf("raw timeout = %s, want 0s", cfg.Defaults.RawTimeout.Duration)
	}
	if !cfg.Defaults.RawTimeout.Set {
		t.Fatal("raw timeout set = false")
	}
}

func TestLoadMissingFileReturnsZeroConfigWithError(t *testing.T) {
	cfg, err := config.Load(filepath.Join(t.TempDir(), "missing.yaml"))
	if err == nil {
		t.Fatal("err = nil")
	}
	if !reflect.DeepEqual(cfg, config.Config{}) {
		t.Fatalf("cfg = %#v, want zero config", cfg)
	}
}

func TestLoadUnknownDefaultFieldReturnsZeroConfigWithError(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	if err := os.WriteFile(path, []byte("version: 1\n\ndefaults:\n  timeuot: 30s\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	cfg, err := config.Load(path)
	if err == nil {
		t.Fatal("err = nil")
	}
	if !reflect.DeepEqual(cfg, config.Config{}) {
		t.Fatalf("cfg = %#v, want zero config", cfg)
	}
}

func TestLoadUnknownPathFieldReturnsZeroConfigWithError(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	if err := os.WriteFile(path, []byte("version: 1\n\npaths:\n  artifact: test-results/mcw\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	cfg, err := config.Load(path)
	if err == nil {
		t.Fatal("err = nil")
	}
	if !reflect.DeepEqual(cfg, config.Config{}) {
		t.Fatalf("cfg = %#v, want zero config", cfg)
	}
}

func TestLoadMultipleYAMLDocumentsReturnsZeroConfigWithError(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	if err := os.WriteFile(path, []byte("version: 1\n---\nunknown: true\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	cfg, err := config.Load(path)
	if err == nil {
		t.Fatal("err = nil")
	}
	if !reflect.DeepEqual(cfg, config.Config{}) {
		t.Fatalf("cfg = %#v, want zero config", cfg)
	}
}

func TestLoadInvalidYAMLReturnsZeroConfigWithError(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	if err := os.WriteFile(path, []byte("defaults:\n  timeout: [30s]\n"), 0o644); err != nil {
		t.Fatal(err)
	}

	cfg, err := config.Load(path)
	if err == nil {
		t.Fatal("err = nil")
	}
	if !reflect.DeepEqual(cfg, config.Config{}) {
		t.Fatalf("cfg = %#v, want zero config", cfg)
	}
}

func writeConfig(t *testing.T, data string) string {
	t.Helper()
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	if err := os.WriteFile(path, []byte(data), 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}
