# Craftwright Milestone 1 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first executable Craftwright foundation: a Go `mcw` CLI, stable output/error contracts, project config loading, an in-memory engine, client lifecycle commands, scenario parsing, and a JSON-RPC stdio daemon that can be tested before the real Minecraft engine is integrated.

**Architecture:** The first slice keeps the product CLI-first and daemon-backed while using an in-memory engine implementation for fast deterministic tests. The engine interface is the boundary where HeadlessMC/HMC-Specifics will attach in the next plan, so CLI commands, scenarios, SDKs, and agents can depend on stable contracts instead of implementation details.

**Tech Stack:** Go 1.22+, Cobra for CLI parsing, `gopkg.in/yaml.v3` for project config and scenarios, Go standard `testing` package, JSON-RPC 2.0 messages over stdio.

---

## Milestone 1 Decisions

- Use JSON-RPC 2.0 over stdio for `mcw daemon --stdio`.
- Use JSONL only for CLI event streams such as `mcw client logs --jsonl`.
- Use `mcw client wait NAME --chat PATTERN` in Milestone 1.
- Use the CLI spec exit code table, including `8` for cache/dependency failures.
- Config precedence in Milestone 1 is flags, environment, project config, built-in defaults. User and system config paths are outside this slice.
- Daemon methods for GUI, render, click, key, command, and disconnect are outside this slice.
- Milestone 1 supports Fabric-oriented offline mode. Vanilla is only a launch-only smoke shape until the real bridge proves what is possible.

## Full Goal Decomposition

This plan is the first executable slice. The full Craftwright goal should be completed through these milestone plans:

1. **Milestone 1 Foundation:** Go CLI, config, output contracts, fake engine, scenario parser, daemon protocol, local verification.
2. **Milestone 1 Real Client Engine:** HeadlessMC/HMC-Specifics integration, offline launch/connect/chat/wait/stop, artifacts, process cleanup.
3. **TypeScript SDK:** daemon client, typed events, fixtures around the same protocol.
4. **Playwright Adapter:** Playwright Test fixture, matchers, trace/artifact integration, parallel worker controls.
5. **Server E2E Examples:** Minekube Gate/Connect smoke suites and generic server examples.
6. **Packaging And Release:** install docs, GitHub Releases, checksums, version injection, license/dependency audit.

The first plan intentionally does not shell out to Minecraft. It creates the tested interfaces and command behavior required before the real engine is added.

## File Structure

- `go.mod`: module metadata and dependencies.
- `cmd/mcw/main.go`: process entrypoint only.
- `internal/cli/root.go`: root command, global flags, command wiring.
- `internal/cli/output.go`: stdout/stderr output modes and structured errors.
- `internal/cli/init.go`: `mcw init` project layout creation.
- `internal/cli/cache.go`: fake `mcw cache prepare/list` cache metadata.
- `internal/cli/client.go`: `mcw client ...` commands.
- `internal/cli/scenario.go`: `mcw scenario ...` commands.
- `internal/cli/daemon.go`: `mcw daemon --stdio` command.
- `internal/config/config.go`: project config loading and defaults.
- `internal/engine/engine.go`: engine interface and domain types.
- `internal/engine/memory.go`: deterministic in-memory engine for tests and early CLI behavior.
- `internal/project/project.go`: project paths for config, cache, and artifacts.
- `internal/scenario/scenario.go`: scenario file parser and runner.
- `internal/daemon/server.go`: JSON-RPC 2.0 stdio server.
- `internal/testutil/cli.go`: test helper for command execution.
- `README.md`: usage and development status update.

## Task 1: Go Module And Root CLI

**Files:**
- Create: `go.mod`
- Create: `cmd/mcw/main.go`
- Create: `internal/cli/root.go`
- Create: `internal/testutil/cli.go`
- Test: `internal/cli/root_test.go`

- [ ] **Step 1: Write the failing root CLI tests**

Create `internal/cli/root_test.go`:

```go
package cli_test

import (
	"bytes"
	"testing"

	"github.com/minekube/craftwright/internal/cli"
	"github.com/minekube/craftwright/internal/engine"
)

func execute(args ...string) (stdout string, stderr string, code int) {
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine: engine.NewMemory(),
		Stdout: &out,
		Stderr: &err,
		Version: "test",
	})
	root.SetArgs(args)
	code = cli.Execute(root)
	return out.String(), err.String(), code
}

func TestRootHelpShowsCommonCommands(t *testing.T) {
	stdout, stderr, code := execute("--help")
	if code != 0 {
		t.Fatalf("code = %d, want 0; stderr = %s", code, stderr)
	}
	if !bytes.Contains([]byte(stdout), []byte("mcw automates real Minecraft Java clients")) {
		t.Fatalf("help missing one-liner:\n%s", stdout)
	}
	if !bytes.Contains([]byte(stdout), []byte("client")) {
		t.Fatalf("help missing client command:\n%s", stdout)
	}
	if !bytes.Contains([]byte(stdout), []byte("scenario")) {
		t.Fatalf("help missing scenario command:\n%s", stdout)
	}
}

func TestVersionPrintsToStdout(t *testing.T) {
	stdout, stderr, code := execute("--version")
	if code != 0 {
		t.Fatalf("code = %d, want 0; stderr = %s", code, stderr)
	}
	if stdout != "mcw test\n" {
		t.Fatalf("stdout = %q", stdout)
	}
	if stderr != "" {
		t.Fatalf("stderr = %q, want empty", stderr)
	}
}

func TestUnknownCommandReturnsUsageError(t *testing.T) {
	_, stderr, code := execute("missing")
	if code != 2 {
		t.Fatalf("code = %d, want 2; stderr = %s", code, stderr)
	}
	if !bytes.Contains([]byte(stderr), []byte("unknown command")) {
		t.Fatalf("stderr missing unknown command message:\n%s", stderr)
	}
}
```

- [ ] **Step 2: Run the root tests to verify they fail**

Run:

```bash
go test ./internal/cli -run 'TestRoot|TestVersion|TestUnknown' -count=1
```

Expected: fail because `go.mod` and the `internal/cli` package do not exist.

- [ ] **Step 3: Add the minimal module and root command**

Create `go.mod`:

```go
module github.com/minekube/craftwright

go 1.22

require github.com/spf13/cobra v1.8.1
```

Create `cmd/mcw/main.go`:

```go
package main

import (
	"os"

	"github.com/minekube/craftwright/internal/cli"
	"github.com/minekube/craftwright/internal/engine"
)

var version = "dev"

func main() {
	root := cli.NewRoot(cli.Dependencies{
		Engine:  engine.NewMemory(),
		Stdout:  os.Stdout,
		Stderr:  os.Stderr,
		Version: version,
	})
	os.Exit(cli.Execute(root))
}
```

Create `internal/cli/root.go`:

```go
package cli

import (
	"fmt"
	"io"

	"github.com/minekube/craftwright/internal/engine"
	"github.com/spf13/cobra"
)

type Dependencies struct {
	Engine  engine.Engine
	Stdout  io.Writer
	Stderr  io.Writer
	Version string
}

type GlobalOptions struct {
	JSON    bool
	JSONL   bool
	Plain   bool
	Quiet   bool
	Verbose int
	Debug   bool
	NoInput bool
	NoColor bool
	Config  string
	WorkDir string
}

func NewRoot(deps Dependencies) *cobra.Command {
	opts := &GlobalOptions{}
	if deps.Stdout == nil {
		deps.Stdout = io.Discard
	}
	if deps.Stderr == nil {
		deps.Stderr = io.Discard
	}
	if deps.Version == "" {
		deps.Version = "dev"
	}
	cmd := &cobra.Command{
		Use:           "mcw",
		Short:         "mcw automates real Minecraft Java clients for tests, agents, and CI.",
		SilenceUsage:  true,
		SilenceErrors: true,
		Version:       "mcw " + deps.Version,
	}
	cmd.SetOut(deps.Stdout)
	cmd.SetErr(deps.Stderr)
	cmd.SetVersionTemplate("{{.Version}}\n")
	cmd.PersistentFlags().BoolVar(&opts.JSON, "json", false, "emit one structured JSON result")
	cmd.PersistentFlags().BoolVar(&opts.JSONL, "jsonl", false, "emit newline-delimited JSON events")
	cmd.PersistentFlags().BoolVar(&opts.Plain, "plain", false, "emit stable line-oriented text")
	cmd.PersistentFlags().BoolVarP(&opts.Quiet, "quiet", "q", false, "suppress non-essential human output")
	cmd.PersistentFlags().CountVarP(&opts.Verbose, "verbose", "v", "increase diagnostics")
	cmd.PersistentFlags().BoolVar(&opts.Debug, "debug", false, "include debug diagnostics")
	cmd.PersistentFlags().BoolVar(&opts.NoInput, "no-input", false, "never prompt")
	cmd.PersistentFlags().BoolVar(&opts.NoColor, "no-color", false, "disable color")
	cmd.PersistentFlags().StringVar(&opts.Config, "config", "", "config file path")
	cmd.PersistentFlags().StringVar(&opts.WorkDir, "work-dir", "", "project working directory")
	cmd.AddCommand(newInitCommand(deps, opts))
	cmd.AddCommand(newCacheCommand(deps, opts))
	cmd.AddCommand(newClientCommand(deps, opts))
	cmd.AddCommand(newScenarioCommand(deps, opts))
	cmd.AddCommand(newDaemonCommand(deps, opts))
	return cmd
}

func Execute(cmd *cobra.Command) int {
	if err := cmd.Execute(); err != nil {
		_, _ = fmt.Fprintf(cmd.ErrOrStderr(), "error: %v\n", err)
		return exitCode(err)
	}
	return 0
}

func exitCode(err error) int {
	return 2
}
```

Create temporary command stubs in `internal/cli/init.go`, `internal/cli/cache.go`, `internal/cli/client.go`, `internal/cli/scenario.go`, and `internal/cli/daemon.go`:

```go
package cli

import "github.com/spf13/cobra"

func newInitCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	return &cobra.Command{Use: "init", Short: "Create Craftwright project files"}
}
```

Use the same package and imports for cache, client, scenario, and daemon, replacing the function names:

```go
func newCacheCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	return &cobra.Command{Use: "cache", Short: "Prepare Minecraft cache files"}
}

func newClientCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	return &cobra.Command{Use: "client", Short: "Manage Minecraft clients"}
}
```

```go
func newScenarioCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	return &cobra.Command{Use: "scenario", Short: "Run declarative scenarios"}
}

func newDaemonCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	return &cobra.Command{Use: "daemon", Short: "Run the machine protocol daemon"}
}
```

Create `internal/engine/engine.go`:

```go
package engine

type Engine interface{}
```

Create `internal/engine/memory.go`:

```go
package engine

func NewMemory() Engine {
	return memoryEngine{}
}

type memoryEngine struct{}
```

- [ ] **Step 4: Run root tests to verify they pass**

Run:

```bash
go test ./internal/cli -run 'TestRoot|TestVersion|TestUnknown' -count=1
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```bash
git add go.mod go.sum cmd/mcw/main.go internal/cli internal/engine
git commit -m "feat: add mcw root CLI"
```

## Task 2: Output Modes And Error Contracts

**Files:**
- Modify: `internal/cli/root.go`
- Create: `internal/cli/output.go`
- Test: `internal/cli/output_test.go`

- [ ] **Step 1: Write failing output contract tests**

Create `internal/cli/output_test.go`:

```go
package cli_test

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/minekube/craftwright/internal/cli"
)

func TestOutputModeRejectsConflictingMachineModes(t *testing.T) {
	_, stderr, code := execute("--json", "--plain", "client")
	if code != 2 {
		t.Fatalf("code = %d, want 2; stderr = %s", code, stderr)
	}
	if !strings.Contains(stderr, "choose only one of --json, --jsonl, or --plain") {
		t.Fatalf("stderr = %q", stderr)
	}
}

func TestWriteJSONResult(t *testing.T) {
	var b strings.Builder
	err := cli.WriteJSON(&b, map[string]any{"ok": true, "name": "alice"})
	if err != nil {
		t.Fatal(err)
	}
	var got map[string]any
	if err := json.Unmarshal([]byte(b.String()), &got); err != nil {
		t.Fatalf("invalid json %q: %v", b.String(), err)
	}
	if got["ok"] != true || got["name"] != "alice" {
		t.Fatalf("got %#v", got)
	}
}
```

- [ ] **Step 2: Run the output tests to verify they fail**

Run:

```bash
go test ./internal/cli -run 'TestOutputMode|TestWriteJSON' -count=1
```

Expected: fail because `WriteJSON` and conflicting output mode validation do not exist.

- [ ] **Step 3: Add output helpers and validation**

Create `internal/cli/output.go`:

```go
package cli

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

var ErrInvalidUsage = errors.New("invalid usage")

type appError struct {
	Code int
	Err  error
}

func (e appError) Error() string {
	return e.Err.Error()
}

func usageError(format string, args ...any) error {
	return appError{Code: 2, Err: fmt.Errorf(format, args...)}
}

func exitCode(err error) int {
	var app appError
	if errors.As(err, &app) {
		return app.Code
	}
	return 1
}

func (o GlobalOptions) Validate() error {
	count := 0
	if o.JSON {
		count++
	}
	if o.JSONL {
		count++
	}
	if o.Plain {
		count++
	}
	if count > 1 {
		return usageError("choose only one of --json, --jsonl, or --plain")
	}
	return nil
}

func WriteJSON(w io.Writer, value any) error {
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	return enc.Encode(value)
}
```

Modify `internal/cli/root.go` so `NewRoot` validates global options before subcommands run and remove the old `exitCode` function:

```go
cmd.PersistentPreRunE = func(cmd *cobra.Command, args []string) error {
	return opts.Validate()
}
```

- [ ] **Step 4: Run output tests to verify they pass**

Run:

```bash
go test ./internal/cli -run 'TestOutputMode|TestWriteJSON' -count=1
```

Expected: selected tests pass.

- [ ] **Step 5: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add internal/cli/root.go internal/cli/output.go internal/cli/output_test.go
git commit -m "feat: define CLI output contracts"
```

## Task 3: Config Defaults And Project Config Loading

**Files:**
- Create: `internal/config/config.go`
- Test: `internal/config/config_test.go`
- Modify: `go.mod`

- [ ] **Step 1: Write failing config tests**

Create `internal/config/config_test.go`:

```go
package config_test

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/minekube/craftwright/internal/config"
)

func TestDefaultsMatchMilestoneOne(t *testing.T) {
	cfg := config.Default()
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
}

func TestLoadProjectConfigOverridesDefaults(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "craftwright.yaml")
	data := []byte("version: 1\n\ndefaults:\n  minecraft: \"1.20.4\"\n  loader: fabric\n  offline: true\n  timeout: 30s\npaths:\n  artifacts: test-results/mcw\n  cache: .cache/mcw\n")
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
	if cfg.Defaults.Timeout != 30*time.Second {
		t.Fatalf("timeout = %s", cfg.Defaults.Timeout)
	}
	if cfg.Paths.Artifacts != "test-results/mcw" {
		t.Fatalf("artifacts = %q", cfg.Paths.Artifacts)
	}
}
```

- [ ] **Step 2: Run config tests to verify they fail**

Run:

```bash
go test ./internal/config -count=1
```

Expected: fail because the `internal/config` package does not exist.

- [ ] **Step 3: Add config loader**

Create `internal/config/config.go`:

```go
package config

import (
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

type Duration struct {
	time.Duration
}

func (d *Duration) UnmarshalYAML(value *yaml.Node) error {
	parsed, err := time.ParseDuration(value.Value)
	if err != nil {
		return err
	}
	d.Duration = parsed
	return nil
}

type Config struct {
	Version  int      `yaml:"version"`
	Defaults Defaults `yaml:"defaults"`
	Paths    Paths    `yaml:"paths"`
}

type Defaults struct {
	Minecraft string        `yaml:"minecraft"`
	Loader    string        `yaml:"loader"`
	Offline   bool          `yaml:"offline"`
	Timeout   time.Duration `yaml:"-"`
	RawTimeout Duration     `yaml:"timeout"`
}

type Paths struct {
	Artifacts string `yaml:"artifacts"`
	Cache     string `yaml:"cache"`
}

func Default() Config {
	return Config{
		Version: 1,
		Defaults: Defaults{
			Minecraft: "1.21.6",
			Loader:    "fabric",
			Offline:   true,
			Timeout:   2 * time.Minute,
			RawTimeout: Duration{
				Duration: 2 * time.Minute,
			},
		},
		Paths: Paths{
			Artifacts: ".craftwright/artifacts",
			Cache:     ".craftwright/cache",
		},
	}
}

func Load(path string) (Config, error) {
	cfg := Default()
	data, err := os.ReadFile(path)
	if err != nil {
		return Config{}, err
	}
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return Config{}, err
	}
	if cfg.Defaults.RawTimeout.Duration != 0 {
		cfg.Defaults.Timeout = cfg.Defaults.RawTimeout.Duration
	}
	return cfg, nil
}
```

Run:

```bash
go get gopkg.in/yaml.v3@v3.0.1
```

- [ ] **Step 4: Run config tests to verify they pass**

Run:

```bash
go test ./internal/config -count=1
```

Expected: config tests pass.

- [ ] **Step 5: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add go.mod go.sum internal/config
git commit -m "feat: load craftwright project config"
```

## Task 4: Project Init, Cache Metadata, And Artifact Paths

**Files:**
- Create: `internal/project/project.go`
- Test: `internal/project/project_test.go`
- Replace: `internal/cli/init.go`
- Replace: `internal/cli/cache.go`
- Test: `internal/cli/init_cache_test.go`

- [ ] **Step 1: Write failing project layout tests**

Create `internal/project/project_test.go`:

```go
package project_test

import (
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
```

- [ ] **Step 2: Run project tests to verify they fail**

Run:

```bash
go test ./internal/project -count=1
```

Expected: fail because `internal/project` does not exist.

- [ ] **Step 3: Implement project layout helpers**

Create `internal/project/project.go`:

```go
package project

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"time"
)

type Layout struct {
	Root string
}

type CacheRequest struct {
	Minecraft string
	Loader    string
	Profile   string
}

type CacheRecord struct {
	Minecraft  string    `json:"minecraft"`
	Loader     string    `json:"loader"`
	Profile    string    `json:"profile"`
	PreparedAt time.Time `json:"preparedAt"`
}

func Init(layout Layout, force bool) error {
	root := layout.Root
	if root == "" {
		root = "."
	}
	if err := os.MkdirAll(filepath.Join(root, ".craftwright/cache"), 0o755); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Join(root, ".craftwright/artifacts"), 0o755); err != nil {
		return err
	}
	configPath := filepath.Join(root, "craftwright.yaml")
	if _, err := os.Stat(configPath); err == nil && !force {
		return nil
	} else if err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	data := []byte("version: 1\n\ndefaults:\n  minecraft: \"1.21.6\"\n  loader: fabric\n  offline: true\n  timeout: 2m\npaths:\n  artifacts: .craftwright/artifacts\n  cache: .craftwright/cache\n")
	return os.WriteFile(configPath, data, 0o644)
}

func PrepareCache(layout Layout, req CacheRequest) (CacheRecord, error) {
	root := layout.Root
	if root == "" {
		root = "."
	}
	profile := req.Profile
	if profile == "" {
		profile = "default"
	}
	record := CacheRecord{Minecraft: req.Minecraft, Loader: req.Loader, Profile: profile, PreparedAt: time.Now().UTC()}
	dir := filepath.Join(root, ".craftwright/cache", profile)
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return CacheRecord{}, err
	}
	data, err := json.MarshalIndent(record, "", "  ")
	if err != nil {
		return CacheRecord{}, err
	}
	path := filepath.Join(dir, req.Minecraft+"-"+req.Loader+".json")
	return record, os.WriteFile(path, append(data, '\n'), 0o644)
}
```

- [ ] **Step 4: Run project tests to verify they pass**

Run:

```bash
go test ./internal/project -count=1
```

Expected: project tests pass.

- [ ] **Step 5: Write failing CLI tests for init and cache prepare**

Create `internal/cli/init_cache_test.go`:

```go
package cli_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestInitCreatesProjectLayout(t *testing.T) {
	dir := t.TempDir()
	stdout, stderr, code := execute("init", "--dir", dir)
	if code != 0 {
		t.Fatalf("code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "Initialized Craftwright project") {
		t.Fatalf("stdout = %q", stdout)
	}
	if _, err := os.Stat(filepath.Join(dir, "craftwright.yaml")); err != nil {
		t.Fatal(err)
	}
}

func TestCachePrepareJSON(t *testing.T) {
	dir := t.TempDir()
	_, _, code := execute("init", "--dir", dir)
	if code != 0 {
		t.Fatalf("init code = %d", code)
	}
	stdout, stderr, code := execute("--json", "--work-dir", dir, "cache", "prepare", "--mc", "1.21.6", "--loader", "fabric")
	if code != 0 {
		t.Fatalf("code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, `"minecraft":"1.21.6"`) {
		t.Fatalf("stdout = %q", stdout)
	}
}
```

- [ ] **Step 6: Run CLI init/cache tests to verify they fail**

Run:

```bash
go test ./internal/cli -run 'TestInit|TestCache' -count=1
```

Expected: fail because init/cache commands are still stubs.

- [ ] **Step 7: Implement init and cache commands**

Replace `internal/cli/init.go`:

```go
package cli

import (
	"fmt"

	"github.com/minekube/craftwright/internal/project"
	"github.com/spf13/cobra"
)

func newInitCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	var dir string
	var dryRun bool
	var force bool
	cmd := &cobra.Command{
		Use:   "init",
		Short: "Create Craftwright project files",
		RunE: func(cmd *cobra.Command, args []string) error {
			if dryRun {
				_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Would initialize Craftwright project at %s\n", dirOrCurrent(dir))
				return nil
			}
			if err := project.Init(project.Layout{Root: dirOrCurrent(dir)}, force); err != nil {
				return err
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Initialized Craftwright project at %s\n", dirOrCurrent(dir))
			return nil
		},
	}
	cmd.Flags().StringVar(&dir, "dir", ".", "project directory")
	cmd.Flags().BoolVar(&dryRun, "dry-run", false, "preview changes")
	cmd.Flags().BoolVar(&force, "force", false, "overwrite compatible files")
	return cmd
}

func dirOrCurrent(dir string) string {
	if dir == "" {
		return "."
	}
	return dir
}
```

Replace `internal/cli/cache.go`:

```go
package cli

import (
	"fmt"

	"github.com/minekube/craftwright/internal/project"
	"github.com/spf13/cobra"
)

func newCacheCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{Use: "cache", Short: "Prepare Minecraft cache files"}
	cmd.AddCommand(newCachePrepareCommand(opts))
	return cmd
}

func newCachePrepareCommand(opts *GlobalOptions) *cobra.Command {
	var mc string
	var loader string
	var profile string
	cmd := &cobra.Command{
		Use:   "prepare",
		Short: "Prepare cache metadata",
		RunE: func(cmd *cobra.Command, args []string) error {
			record, err := project.PrepareCache(project.Layout{Root: dirOrCurrent(opts.WorkDir)}, project.CacheRequest{Minecraft: mc, Loader: loader, Profile: profile})
			if err != nil {
				return appError{Code: 8, Err: err}
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "cache": record})
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Prepared Minecraft %s with %s cache profile %s\n", record.Minecraft, record.Loader, record.Profile)
			return nil
		},
	}
	cmd.Flags().StringVar(&mc, "mc", "", "Minecraft version")
	cmd.Flags().StringVar(&loader, "loader", "fabric", "mod loader")
	cmd.Flags().StringVar(&profile, "profile", "default", "cache profile")
	_ = cmd.MarkFlagRequired("mc")
	return cmd
}
```

- [ ] **Step 8: Run CLI init/cache tests to verify they pass**

Run:

```bash
go test ./internal/cli -run 'TestInit|TestCache' -count=1
```

Expected: CLI init/cache tests pass.

- [ ] **Step 9: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 10: Commit**

```bash
git add internal/project internal/cli/init.go internal/cli/cache.go internal/cli/init_cache_test.go
git commit -m "feat: add project init and cache metadata"
```

## Task 5: Engine Interface And In-Memory Client Lifecycle

**Files:**
- Replace: `internal/engine/engine.go`
- Replace: `internal/engine/memory.go`
- Test: `internal/engine/memory_test.go`

- [ ] **Step 1: Write failing engine lifecycle tests**

Create `internal/engine/memory_test.go`:

```go
package engine_test

import (
	"context"
	"testing"
	"time"

	"github.com/minekube/craftwright/internal/engine"
)

func TestMemoryEngineLaunchConnectChatWaitStop(t *testing.T) {
	ctx := context.Background()
	e := engine.NewMemory()
	client, err := e.Launch(ctx, engine.LaunchRequest{Name: "alice", MinecraftVersion: "1.21.6", Loader: "fabric", Offline: true})
	if err != nil {
		t.Fatal(err)
	}
	if client.Name != "alice" || client.State != engine.StateRunning {
		t.Fatalf("client = %#v", client)
	}
	if err := e.Connect(ctx, "alice", "localhost:25565"); err != nil {
		t.Fatal(err)
	}
	if err := e.Chat(ctx, "alice", "hello"); err != nil {
		t.Fatal(err)
	}
	event, err := e.Wait(ctx, engine.WaitRequest{Client: "alice", ChatPattern: "hello", Timeout: time.Second})
	if err != nil {
		t.Fatal(err)
	}
	if event.Type != engine.EventChat || event.Message != "hello" {
		t.Fatalf("event = %#v", event)
	}
	logs, err := e.Logs(ctx, "alice")
	if err != nil {
		t.Fatal(err)
	}
	if len(logs) == 0 || logs[len(logs)-1] != "CHAT hello" {
		t.Fatalf("logs = %#v", logs)
	}
	if err := e.Stop(ctx, "alice", false); err != nil {
		t.Fatal(err)
	}
	stopped, err := e.Status(ctx, "alice")
	if err != nil {
		t.Fatal(err)
	}
	if stopped.State != engine.StateStopped {
		t.Fatalf("state = %s", stopped.State)
	}
}

func TestMemoryEngineRejectsDuplicateRunningClient(t *testing.T) {
	ctx := context.Background()
	e := engine.NewMemory()
	_, err := e.Launch(ctx, engine.LaunchRequest{Name: "alice", MinecraftVersion: "1.21.6", Loader: "fabric", Offline: true})
	if err != nil {
		t.Fatal(err)
	}
	_, err = e.Launch(ctx, engine.LaunchRequest{Name: "alice", MinecraftVersion: "1.21.6", Loader: "fabric", Offline: true})
	if err == nil {
		t.Fatal("expected duplicate launch error")
	}
}
```

- [ ] **Step 2: Run engine tests to verify they fail**

Run:

```bash
go test ./internal/engine -count=1
```

Expected: fail because the engine interface methods do not exist.

- [ ] **Step 3: Replace engine types and memory implementation**

Replace `internal/engine/engine.go`:

```go
package engine

import (
	"context"
	"time"
)

type State string

const (
	StateRunning   State = "running"
	StateConnected State = "connected"
	StateStopped   State = "stopped"
)

type EventType string

const (
	EventChat  EventType = "client.chat"
	EventState EventType = "client.state"
)

type Client struct {
	Name             string `json:"name"`
	State            State  `json:"state"`
	MinecraftVersion string `json:"minecraftVersion"`
	Loader           string `json:"loader"`
	Offline          bool   `json:"offline"`
	Server           string `json:"server,omitempty"`
}

type Event struct {
	Type    EventType `json:"type"`
	Client  string    `json:"client"`
	Message string    `json:"message,omitempty"`
	State   State     `json:"state,omitempty"`
}

type LaunchRequest struct {
	Name             string
	MinecraftVersion string
	Loader           string
	Offline          bool
	Username         string
	Server           string
	Timeout          time.Duration
	ArtifactsDir     string
}

type WaitRequest struct {
	Client      string
	ChatPattern string
	Timeout     time.Duration
}

type Engine interface {
	Launch(context.Context, LaunchRequest) (Client, error)
	List(context.Context) ([]Client, error)
	Status(context.Context, string) (Client, error)
	Connect(context.Context, string, string) error
	Chat(context.Context, string, string) error
	Wait(context.Context, WaitRequest) (Event, error)
	Logs(context.Context, string) ([]string, error)
	Stop(context.Context, string, bool) error
}
```

Replace `internal/engine/memory.go`:

```go
package engine

import (
	"context"
	"fmt"
	"regexp"
	"sync"
)

type memoryEngine struct {
	mu      sync.Mutex
	clients map[string]Client
	events  []Event
	logs    map[string][]string
}

func NewMemory() Engine {
	return &memoryEngine{
		clients: map[string]Client{},
		logs:    map[string][]string{},
	}
}

func (m *memoryEngine) Launch(ctx context.Context, req LaunchRequest) (Client, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if existing, ok := m.clients[req.Name]; ok && existing.State != StateStopped {
		return Client{}, fmt.Errorf("client %s is already running", req.Name)
	}
	client := Client{Name: req.Name, State: StateRunning, MinecraftVersion: req.MinecraftVersion, Loader: req.Loader, Offline: req.Offline, Server: req.Server}
	m.clients[req.Name] = client
	m.events = append(m.events, Event{Type: EventState, Client: req.Name, State: StateRunning})
	m.logs[req.Name] = append(m.logs[req.Name], "LAUNCH "+req.MinecraftVersion+" "+req.Loader)
	if req.Server != "" {
		client.State = StateConnected
		client.Server = req.Server
		m.clients[req.Name] = client
		m.events = append(m.events, Event{Type: EventState, Client: req.Name, State: StateConnected})
		m.logs[req.Name] = append(m.logs[req.Name], "CONNECT "+req.Server)
	}
	return client, nil
}

func (m *memoryEngine) List(ctx context.Context) ([]Client, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]Client, 0, len(m.clients))
	for _, client := range m.clients {
		out = append(out, client)
	}
	return out, nil
}

func (m *memoryEngine) Status(ctx context.Context, name string) (Client, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	client, ok := m.clients[name]
	if !ok {
		return Client{}, fmt.Errorf("client %s not found", name)
	}
	return client, nil
}

func (m *memoryEngine) Connect(ctx context.Context, name string, server string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	client, ok := m.clients[name]
	if !ok {
		return fmt.Errorf("client %s not found", name)
	}
	client.State = StateConnected
	client.Server = server
	m.clients[name] = client
	m.events = append(m.events, Event{Type: EventState, Client: name, State: StateConnected})
	m.logs[name] = append(m.logs[name], "CONNECT "+server)
	return nil
}

func (m *memoryEngine) Chat(ctx context.Context, name string, message string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.clients[name]; !ok {
		return fmt.Errorf("client %s not found", name)
	}
	m.events = append(m.events, Event{Type: EventChat, Client: name, Message: message})
	m.logs[name] = append(m.logs[name], "CHAT "+message)
	return nil
}

func (m *memoryEngine) Wait(ctx context.Context, req WaitRequest) (Event, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	pattern := req.ChatPattern
	if len(pattern) >= 2 && pattern[0] == '/' && pattern[len(pattern)-1] == '/' {
		pattern = pattern[1 : len(pattern)-1]
	}
	re, err := regexp.Compile(pattern)
	if err != nil {
		return Event{}, err
	}
	for _, event := range m.events {
		if event.Client == req.Client && event.Type == EventChat && re.MatchString(event.Message) {
			return event, nil
		}
	}
	return Event{}, fmt.Errorf("timed out waiting for chat %q", req.ChatPattern)
}

func (m *memoryEngine) Logs(ctx context.Context, name string) ([]string, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.clients[name]; !ok {
		return nil, fmt.Errorf("client %s not found", name)
	}
	out := append([]string(nil), m.logs[name]...)
	return out, nil
}

func (m *memoryEngine) Stop(ctx context.Context, name string, force bool) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	client, ok := m.clients[name]
	if !ok {
		return nil
	}
	client.State = StateStopped
	m.clients[name] = client
	m.events = append(m.events, Event{Type: EventState, Client: name, State: StateStopped})
	m.logs[name] = append(m.logs[name], "STOP")
	return nil
}
```

- [ ] **Step 4: Run engine tests to verify they pass**

Run:

```bash
go test ./internal/engine -count=1
```

Expected: engine tests pass.

- [ ] **Step 5: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add internal/engine
git commit -m "feat: add engine lifecycle interface"
```

## Task 6: Client Commands Backed By Engine

**Files:**
- Replace: `internal/cli/client.go`
- Test: `internal/cli/client_test.go`

- [ ] **Step 1: Write failing client command tests**

Create `internal/cli/client_test.go`:

```go
package cli_test

import (
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	"github.com/minekube/craftwright/internal/cli"
	"github.com/minekube/craftwright/internal/engine"
)

func executeWithEngine(e engine.Engine, args ...string) (stdout string, stderr string, code int) {
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine: e,
		Stdout: &out,
		Stderr: &err,
		Version: "test",
	})
	root.SetArgs(args)
	code = cli.Execute(root)
	return out.String(), err.String(), code
}

func TestClientLaunchJSON(t *testing.T) {
	stdout, stderr, code := execute("--json", "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("code = %d stderr = %s", code, stderr)
	}
	var got map[string]any
	if err := json.Unmarshal([]byte(stdout), &got); err != nil {
		t.Fatal(err)
	}
	if got["ok"] != true {
		t.Fatalf("got %#v", got)
	}
}

func TestClientLifecycleHumanCommands(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}
	stdout, stderr, code := executeWithEngine(e, "client", "connect", "alice", "localhost:25565")
	if code != 0 {
		t.Fatalf("connect code = %d stderr = %s", code, stderr)
	}
	if !strings.Contains(stdout, "Connected client alice to localhost:25565") {
		t.Fatalf("stdout = %q", stdout)
	}
}
```

- [ ] **Step 2: Run client command tests to verify they fail**

Run:

```bash
go test ./internal/cli -run 'TestClient' -count=1
```

Expected: fail because `client launch` has no command handler yet.

- [ ] **Step 3: Implement minimal client commands**

Replace `internal/cli/client.go`:

```go
package cli

import (
	"context"
	"fmt"
	"time"

	"github.com/minekube/craftwright/internal/engine"
	"github.com/spf13/cobra"
)

func newClientCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{Use: "client", Short: "Manage Minecraft clients"}
	cmd.AddCommand(newClientLaunchCommand(deps, opts))
	return cmd
}

func newClientLaunchCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	var mc string
	var loader string
	var offline bool
	var username string
	var server string
	var timeout time.Duration
	var artifacts string
	cmd := &cobra.Command{
		Use:   "launch NAME",
		Short: "Launch a Minecraft client",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			client, err := deps.Engine.Launch(context.Background(), engine.LaunchRequest{
				Name:             args[0],
				MinecraftVersion: mc,
				Loader:           loader,
				Offline:          offline,
				Username:         username,
				Server:           server,
				Timeout:          timeout,
				ArtifactsDir:     artifacts,
			})
			if err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": client})
			}
			_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Launched client %s\nMinecraft: %s\nMode: offline\n", client.Name, client.MinecraftVersion)
			if server != "" {
				_, _ = fmt.Fprintf(cmd.OutOrStdout(), "Server: %s\n", server)
			}
			return nil
		},
	}
	cmd.Flags().StringVar(&mc, "mc", "", "Minecraft version")
	cmd.Flags().StringVar(&loader, "loader", "fabric", "mod loader")
	cmd.Flags().BoolVar(&offline, "offline", true, "use offline mode")
	cmd.Flags().StringVar(&username, "username", "", "offline username")
	cmd.Flags().StringVar(&server, "server", "", "server to connect after launch")
	cmd.Flags().DurationVar(&timeout, "timeout", 2*time.Minute, "launch timeout")
	cmd.Flags().StringVar(&artifacts, "artifacts", "", "artifact directory")
	_ = cmd.MarkFlagRequired("mc")
	return cmd
}
```

- [ ] **Step 4: Run client command tests to verify they pass**

Run:

```bash
go test ./internal/cli -run 'TestClient' -count=1
```

Expected: selected client tests pass.

- [ ] **Step 5: Write failing tests for each remaining client command**

Append these tests to `internal/cli/client_test.go`:

```go
func TestClientListAndStatusPlain(t *testing.T) {
	e := engine.NewMemory()
	_, _, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d", code)
	}
	stdout, stderr, code := executeWithEngine(e, "--plain", "client", "list")
	if code != 0 {
		t.Fatalf("list code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "alice running 1.21.6 offline") {
		t.Fatalf("list stdout = %q", stdout)
	}
	stdout, stderr, code = executeWithEngine(e, "--plain", "client", "status", "alice")
	if code != 0 {
		t.Fatalf("status code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "alice running 1.21.6 offline") {
		t.Fatalf("status stdout = %q", stdout)
	}
}

func TestClientWaitChatPlain(t *testing.T) {
	e := engine.NewMemory()
	stdout, stderr, code := executeWithEngine(e, "--plain", "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	stdout, stderr, code = executeWithEngine(e, "client", "chat", "alice", "Welcome alice")
	if code != 0 {
		t.Fatalf("chat code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	stdout, stderr, code = executeWithEngine(e, "--plain", "client", "wait", "alice", "--chat", "/Welcome/")
	if code != 0 {
		t.Fatalf("wait code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "client.chat alice Welcome alice") {
		t.Fatalf("stdout = %q", stdout)
	}
}

func TestClientLogsAndStop(t *testing.T) {
	e := engine.NewMemory()
	_, _, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d", code)
	}
	_, _, code = executeWithEngine(e, "client", "chat", "alice", "hello")
	if code != 0 {
		t.Fatalf("chat code = %d", code)
	}
	stdout, stderr, code := executeWithEngine(e, "client", "logs", "alice")
	if code != 0 {
		t.Fatalf("logs code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "CHAT hello") {
		t.Fatalf("logs stdout = %q", stdout)
	}
	stdout, stderr, code = executeWithEngine(e, "client", "stop", "alice")
	if code != 0 {
		t.Fatalf("stop code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "Stopped client alice") {
		t.Fatalf("stop stdout = %q", stdout)
	}
}
```

- [ ] **Step 6: Add list/status/connect/chat/wait/logs/stop constructors**

Extend `internal/cli/client.go` with these command constructors and add them from `newClientCommand`:

```go
cmd.AddCommand(newClientListCommand(deps, opts))
cmd.AddCommand(newClientStatusCommand(deps, opts))
cmd.AddCommand(newClientConnectCommand(deps, opts))
cmd.AddCommand(newClientChatCommand(deps, opts))
cmd.AddCommand(newClientWaitCommand(deps, opts))
cmd.AddCommand(newClientLogsCommand(deps, opts))
cmd.AddCommand(newClientStopCommand(deps, opts))
```

Each constructor calls exactly one engine method, writes JSON with `WriteJSON` when `opts.JSON` is set, writes stable fields when `opts.Plain` is set, and writes compact human output otherwise. The required plain formats are:

```text
client list/status: NAME STATE MINECRAFT_VERSION offline|online
client wait: EVENT_TYPE CLIENT MESSAGE
client logs: one log line per output line
```

- [ ] **Step 7: Run all CLI tests**

Run:

```bash
go test ./internal/cli -count=1
```

Expected: CLI tests pass.

- [ ] **Step 8: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add internal/cli
git commit -m "feat: wire client commands to engine"
```

## Task 7: Scenario Parser And Runner

**Files:**
- Create: `internal/scenario/scenario.go`
- Test: `internal/scenario/scenario_test.go`
- Replace: `internal/cli/scenario.go`
- Test: `internal/cli/scenario_test.go`

- [ ] **Step 1: Write failing scenario parser tests**

Create `internal/scenario/scenario_test.go`:

```go
package scenario_test

import (
	"context"
	"os"
	"path/filepath"
	"testing"

	"github.com/minekube/craftwright/internal/engine"
	"github.com/minekube/craftwright/internal/scenario"
)

func TestRunScenarioLaunchConnectWaitChat(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "gate-smoke.yaml")
	data := []byte(`version: 1
clients:
  alice:
    mc: "1.21.6"
    loader: fabric
    offline: true
steps:
  - launch: alice
  - connect:
      client: alice
      server: "localhost:25565"
  - chat:
      client: alice
      message: "Welcome alice"
  - wait:
      client: alice
      chat: "/Welcome/"
      timeout: 30s
`)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatal(err)
	}
	result, err := scenario.RunFile(context.Background(), engine.NewMemory(), path)
	if err != nil {
		t.Fatal(err)
	}
	if !result.OK || result.Steps != 4 {
		t.Fatalf("result = %#v", result)
	}
}
```

- [ ] **Step 2: Run scenario tests to verify they fail**

Run:

```bash
go test ./internal/scenario -count=1
```

Expected: fail because the `internal/scenario` package does not exist.

- [ ] **Step 3: Implement scenario parser and runner**

Create `internal/scenario/scenario.go`:

```go
package scenario

import (
	"context"
	"os"
	"time"

	"github.com/minekube/craftwright/internal/engine"
	"gopkg.in/yaml.v3"
)

type File struct {
	Version int                `yaml:"version"`
	Clients map[string]Client `yaml:"clients"`
	Steps   []Step            `yaml:"steps"`
}

type Client struct {
	MC      string `yaml:"mc"`
	Loader  string `yaml:"loader"`
	Offline bool   `yaml:"offline"`
}

type Step struct {
	Launch  string       `yaml:"launch,omitempty"`
	Connect *ConnectStep `yaml:"connect,omitempty"`
	Chat    *ChatStep    `yaml:"chat,omitempty"`
	Wait    *WaitStep    `yaml:"wait,omitempty"`
}

type ConnectStep struct {
	Client string `yaml:"client"`
	Server string `yaml:"server"`
}

type ChatStep struct {
	Client  string `yaml:"client"`
	Message string `yaml:"message"`
}

type WaitStep struct {
	Client  string `yaml:"client"`
	Chat    string `yaml:"chat"`
	Timeout string `yaml:"timeout"`
}

type Result struct {
	OK    bool `json:"ok"`
	Steps int  `json:"steps"`
}

func RunFile(ctx context.Context, eng engine.Engine, path string) (Result, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return Result{}, err
	}
	var file File
	if err := yaml.Unmarshal(data, &file); err != nil {
		return Result{}, err
	}
	steps := 0
	for _, step := range file.Steps {
		switch {
		case step.Launch != "":
			client := file.Clients[step.Launch]
			_, err = eng.Launch(ctx, engine.LaunchRequest{Name: step.Launch, MinecraftVersion: client.MC, Loader: client.Loader, Offline: client.Offline})
		case step.Connect != nil:
			err = eng.Connect(ctx, step.Connect.Client, step.Connect.Server)
		case step.Chat != nil:
			err = eng.Chat(ctx, step.Chat.Client, step.Chat.Message)
		case step.Wait != nil:
			timeout := 30 * time.Second
			if step.Wait.Timeout != "" {
				timeout, err = time.ParseDuration(step.Wait.Timeout)
				if err != nil {
					return Result{}, err
				}
			}
			_, err = eng.Wait(ctx, engine.WaitRequest{Client: step.Wait.Client, ChatPattern: step.Wait.Chat, Timeout: timeout})
		}
		if err != nil {
			return Result{}, err
		}
		steps++
	}
	return Result{OK: true, Steps: steps}, nil
}
```

- [ ] **Step 4: Run scenario package tests to verify they pass**

Run:

```bash
go test ./internal/scenario -count=1
```

Expected: scenario package tests pass.

- [ ] **Step 5: Add failing CLI scenario tests**

Create `internal/cli/scenario_test.go` with a temp scenario file and assertions for:

```go
stdout, stderr, code := execute("--json", "scenario", "run", path)
```

Expected assertions:

```go
if code != 0 {
	t.Fatalf("code = %d stderr = %s stdout = %s", code, stderr, stdout)
}
if !strings.Contains(stdout, `"ok":true`) {
	t.Fatalf("stdout = %q", stdout)
}
```

- [ ] **Step 6: Replace scenario CLI command**

Replace `internal/cli/scenario.go` with `scenario run FILE` and `scenario validate FILE`. `run` calls `scenario.RunFile`; `validate` loads YAML and reports success without executing steps. JSON mode uses `WriteJSON`.

- [ ] **Step 7: Run scenario CLI tests**

Run:

```bash
go test ./internal/cli -run 'TestScenario' -count=1
```

Expected: scenario CLI tests pass.

- [ ] **Step 8: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```bash
git add internal/scenario internal/cli/scenario.go internal/cli/scenario_test.go
git commit -m "feat: run declarative scenarios"
```

## Task 8: JSON-RPC Stdio Daemon

**Files:**
- Create: `internal/daemon/server.go`
- Test: `internal/daemon/server_test.go`
- Replace: `internal/cli/daemon.go`
- Test: `internal/cli/daemon_test.go`

- [ ] **Step 1: Write failing daemon protocol test**

Create `internal/daemon/server_test.go`:

```go
package daemon_test

import (
	"bufio"
	"context"
	"encoding/json"
	"strings"
	"testing"

	"github.com/minekube/craftwright/internal/daemon"
	"github.com/minekube/craftwright/internal/engine"
)

func TestServerLaunchAndStatus(t *testing.T) {
	input := strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"client.launch","params":{"name":"alice","minecraftVersion":"1.21.6","loader":"fabric","offline":true}}` + "\n" +
		`{"jsonrpc":"2.0","id":2,"method":"client.status","params":{"name":"alice"}}` + "\n")
	var output strings.Builder
	err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output)
	if err != nil {
		t.Fatal(err)
	}
	scanner := bufio.NewScanner(strings.NewReader(output.String()))
	var lines []map[string]any
	for scanner.Scan() {
		var line map[string]any
		if err := json.Unmarshal(scanner.Bytes(), &line); err != nil {
			t.Fatal(err)
		}
		lines = append(lines, line)
	}
	if len(lines) != 2 {
		t.Fatalf("lines = %d output = %s", len(lines), output.String())
	}
	if lines[0]["id"].(float64) != 1 || lines[1]["id"].(float64) != 2 {
		t.Fatalf("responses = %#v", lines)
	}
}
```

- [ ] **Step 2: Run daemon tests to verify they fail**

Run:

```bash
go test ./internal/daemon -count=1
```

Expected: fail because the daemon package does not exist.

- [ ] **Step 3: Implement minimal JSON-RPC server**

Create `internal/daemon/server.go`:

```go
package daemon

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"

	"github.com/minekube/craftwright/internal/engine"
)

type request struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      int             `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params"`
}

type response struct {
	JSONRPC string `json:"jsonrpc"`
	ID      int    `json:"id"`
	Result  any    `json:"result,omitempty"`
	Error   any    `json:"error,omitempty"`
}

func Serve(ctx context.Context, eng engine.Engine, in io.Reader, out io.Writer) error {
	scanner := bufio.NewScanner(in)
	enc := json.NewEncoder(out)
	for scanner.Scan() {
		var req request
		if err := json.Unmarshal(scanner.Bytes(), &req); err != nil {
			return err
		}
		result, err := handle(ctx, eng, req)
		res := response{JSONRPC: "2.0", ID: req.ID, Result: result}
		if err != nil {
			res.Result = nil
			res.Error = map[string]any{"code": -32000, "message": err.Error()}
		}
		if err := enc.Encode(res); err != nil {
			return err
		}
	}
	return scanner.Err()
}

func handle(ctx context.Context, eng engine.Engine, req request) (any, error) {
	switch req.Method {
	case "client.launch":
		var params engine.LaunchRequest
		if err := json.Unmarshal(req.Params, &params); err != nil {
			return nil, err
		}
		return eng.Launch(ctx, params)
	case "client.status":
		var params struct {
			Name string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &params); err != nil {
			return nil, err
		}
		return eng.Status(ctx, params.Name)
	default:
		return nil, fmt.Errorf("unknown method %s", req.Method)
	}
}
```

- [ ] **Step 4: Run daemon package tests to verify they pass**

Run:

```bash
go test ./internal/daemon -count=1
```

Expected: daemon package tests pass.

- [ ] **Step 5: Wire `mcw daemon --stdio`**

Replace `internal/cli/daemon.go` so `mcw daemon --stdio` calls `daemon.Serve(context.Background(), deps.Engine, os.Stdin, deps.Stdout)` through injectable readers in tests. Add `internal/cli/daemon_test.go` to assert `mcw daemon --help` contains `--stdio`.

- [ ] **Step 6: Run daemon CLI tests**

Run:

```bash
go test ./internal/cli -run 'TestDaemon' -count=1
```

Expected: daemon CLI tests pass.

- [ ] **Step 7: Run all current Go tests**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add internal/daemon internal/cli/daemon.go internal/cli/daemon_test.go
git commit -m "feat: add stdio daemon protocol"
```

## Task 9: README Status And Local Verification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update README with current executable scope**

Update `README.md` to include:

````markdown
## Status

Craftwright is in early Milestone 1 development.

The first executable slice provides the `mcw` CLI, machine-readable output
contracts, project config, an in-memory engine, scenario parsing, and a stdio
daemon protocol. The real Minecraft client backend is the next milestone and
will attach behind the same engine interface.

## Development

```sh
go test ./... -count=1
```
````

- [ ] **Step 2: Run local verification**

Run:

```bash
go test ./... -count=1
```

Expected: all tests pass.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: describe milestone 1 foundation"
```

## Plan Self-Review Checklist

Before executing this plan:

- [ ] Each task starts with a failing test or an explicit documentation change.
- [ ] Each implementation task has a verification command and expected result.
- [ ] Every commit keeps the repository in a testable state.
- [ ] The fake engine is clearly isolated behind `internal/engine.Engine`.
- [ ] No SDK, Playwright adapter, or real-client backend is mixed into this foundation slice.
- [ ] The next real-client plan can replace or add an engine implementation without rewriting CLI/scenario/daemon tests.
