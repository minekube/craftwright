package config

import (
	"bytes"
	"errors"
	"io"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

type Duration struct {
	time.Duration
	Set bool `yaml:"-"`
}

func (d *Duration) UnmarshalYAML(value *yaml.Node) error {
	var raw string
	if err := value.Decode(&raw); err != nil {
		return err
	}

	parsed, err := time.ParseDuration(raw)
	if err != nil {
		return err
	}

	d.Duration = parsed
	d.Set = true
	return nil
}

type Config struct {
	Version  int      `yaml:"version"`
	Defaults Defaults `yaml:"defaults"`
	Paths    Paths    `yaml:"paths"`
	Backend  Backend  `yaml:"backend"`
}

type Defaults struct {
	Minecraft  string        `yaml:"minecraft"`
	Loader     string        `yaml:"loader"`
	Offline    bool          `yaml:"offline"`
	Timeout    time.Duration `yaml:"-"`
	RawTimeout Duration      `yaml:"timeout"`
}

type Paths struct {
	Artifacts string `yaml:"artifacts"`
	Cache     string `yaml:"cache"`
}

type Backend struct {
	Type              string   `yaml:"type"`
	HeadlessMCVersion string   `yaml:"headlessmcVersion"`
	SpecificsVersion  string   `yaml:"specificsVersion"`
	Java              string   `yaml:"java"`
	JVMArgs           []string `yaml:"jvmArgs"`
}

func Default() Config {
	return Config{
		Version: 1,
		Defaults: Defaults{
			Minecraft:  "1.21.6",
			Loader:     "fabric",
			Offline:    true,
			Timeout:    2 * time.Minute,
			RawTimeout: Duration{Duration: 2 * time.Minute},
		},
		Paths: Paths{
			Artifacts: ".craftwright/artifacts",
			Cache:     ".craftwright/cache",
		},
		Backend: Backend{
			Type:              "memory",
			HeadlessMCVersion: "2.9.0",
			SpecificsVersion:  "2.4.0",
			Java:              "java",
			JVMArgs: []string{
				"-Djava.awt.headless=true",
				"-Xmx2G",
			},
		},
	}
}

func Load(path string) (Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Config{}, err
	}

	cfg := Default()
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	if err := decoder.Decode(&cfg); err != nil {
		return Config{}, err
	}
	var extra yaml.Node
	if err := decoder.Decode(&extra); err != io.EOF {
		if err != nil {
			return Config{}, err
		}
		return Config{}, errors.New("multiple YAML documents are not supported")
	}
	if cfg.Defaults.RawTimeout.Set {
		cfg.Defaults.Timeout = cfg.Defaults.RawTimeout.Duration
	}

	return cfg, nil
}
