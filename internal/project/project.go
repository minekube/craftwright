package project

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/minekube/craftwright/internal/config"
)

type Layout struct {
	Root string `json:"root"`
}

type CacheRequest struct {
	Minecraft string `json:"minecraft"`
	Loader    string `json:"loader"`
	Profile   string `json:"profile"`
}

type CacheRecord struct {
	Minecraft  string    `json:"minecraft"`
	Loader     string    `json:"loader"`
	Profile    string    `json:"profile"`
	PreparedAt time.Time `json:"preparedAt"`
}

const configFile = "craftwright.yaml"

const defaultConfig = `version: 1

defaults:
  minecraft: "1.21.6"
  loader: fabric
  offline: true
  timeout: 2m
paths:
  artifacts: .craftwright/artifacts
  cache: .craftwright/cache
backend:
  type: memory
  headlessmcVersion: "2.9.0"
  specificsVersion: "2.4.0"
  java: java
  jvmArgs:
    - "-Djava.awt.headless=true"
    - "-Xmx2G"
`

func Init(layout Layout, force bool) error {
	root := layout.Root
	if root == "" {
		root = "."
	}

	configPath := filepath.Join(root, configFile)
	configExists := false
	if _, err := os.Stat(configPath); err == nil {
		configExists = true
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	if configExists && !force {
		if _, err := config.Load(configPath); err != nil {
			return err
		}
	}

	for _, dir := range []string{
		filepath.Join(root, ".craftwright", "cache"),
		filepath.Join(root, ".craftwright", "artifacts"),
	} {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	if configExists && !force {
		return nil
	}

	return os.WriteFile(configPath, []byte(defaultConfig), 0o644)
}

func PrepareCache(layout Layout, req CacheRequest) (CacheRecord, error) {
	root := layout.Root
	if root == "" {
		root = "."
	}
	if req.Profile == "" {
		req.Profile = "default"
	}
	if err := validateIdentifier("minecraft", req.Minecraft); err != nil {
		return CacheRecord{}, err
	}
	if err := validateIdentifier("loader", req.Loader); err != nil {
		return CacheRecord{}, err
	}
	if err := validateIdentifier("profile", req.Profile); err != nil {
		return CacheRecord{}, err
	}

	record := CacheRecord{
		Minecraft:  req.Minecraft,
		Loader:     req.Loader,
		Profile:    req.Profile,
		PreparedAt: time.Now().UTC(),
	}
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return CacheRecord{}, err
	}
	data = append(data, '\n')

	dir := filepath.Join(root, ".craftwright", "cache", req.Profile)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return CacheRecord{}, err
	}
	path := filepath.Join(dir, req.Minecraft+"-"+req.Loader+".json")
	existing, err := os.ReadFile(path)
	if err == nil {
		var existingRecord CacheRecord
		if err := json.Unmarshal(existing, &existingRecord); err != nil {
			return CacheRecord{}, fmt.Errorf("read cache metadata: %w", err)
		}
		if existingRecord.Minecraft != req.Minecraft || existingRecord.Loader != req.Loader || existingRecord.Profile != req.Profile {
			return CacheRecord{}, fmt.Errorf("cache metadata does not match request")
		}
		return existingRecord, nil
	} else if !errors.Is(err, os.ErrNotExist) {
		return CacheRecord{}, err
	}
	if err := os.WriteFile(path, data, 0o644); err != nil {
		return CacheRecord{}, err
	}

	return record, nil
}

func validateIdentifier(name, value string) error {
	if value == "" {
		return fmt.Errorf("%s is required", name)
	}
	if value == "." || value == ".." {
		return fmt.Errorf("%s %q is not allowed", name, value)
	}
	for _, r := range value {
		if r >= 'a' && r <= 'z' {
			continue
		}
		if r >= 'A' && r <= 'Z' {
			continue
		}
		if r >= '0' && r <= '9' {
			continue
		}
		if r == '.' || r == '_' || r == '-' {
			continue
		}
		return fmt.Errorf("%s %q contains unsupported character %q", name, value, r)
	}
	return nil
}
