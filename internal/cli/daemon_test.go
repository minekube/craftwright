package cli_test

import (
	"bufio"
	"bytes"
	"encoding/json"
	"strings"
	"testing"

	"github.com/minekube/craftwright/internal/cli"
	"github.com/minekube/craftwright/internal/engine"
)

func TestDaemonHelpShowsStdioFlag(t *testing.T) {
	stdout, stderr, code := execute("daemon", "--help")
	if code != 0 {
		t.Fatalf("code = %d, want 0; stderr = %s", code, stderr)
	}
	if !bytes.Contains([]byte(stdout), []byte("--stdio")) {
		t.Fatalf("help missing --stdio flag:\n%s", stdout)
	}
}

func TestDaemonStdioServesLaunchAndStatus(t *testing.T) {
	input := strings.NewReader(
		`{"jsonrpc":"2.0","id":1,"method":"client.launch","params":{"name":"alice","minecraftVersion":"1.21.6","loader":"fabric","offline":true}}` + "\n" +
			`{"jsonrpc":"2.0","id":2,"method":"client.status","params":{"name":"alice"}}` + "\n",
	)
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine:  engine.NewMemory(),
		Stdin:   input,
		Stdout:  &out,
		Stderr:  &err,
		Version: "test",
	})
	root.SetArgs([]string{"daemon", "--stdio"})

	code := cli.Execute(root)
	if code != 0 {
		t.Fatalf("code = %d, want 0; stderr = %s stdout = %s", code, err.String(), out.String())
	}
	if err.String() != "" {
		t.Fatalf("stderr = %q, want empty", err.String())
	}

	var responses []struct {
		JSONRPC string          `json:"jsonrpc"`
		ID      int             `json:"id"`
		Result  json.RawMessage `json:"result"`
		Error   any             `json:"error,omitempty"`
	}
	scanner := bufio.NewScanner(&out)
	for scanner.Scan() {
		var response struct {
			JSONRPC string          `json:"jsonrpc"`
			ID      int             `json:"id"`
			Result  json.RawMessage `json:"result"`
			Error   any             `json:"error,omitempty"`
		}
		if err := json.Unmarshal(scanner.Bytes(), &response); err != nil {
			t.Fatalf("response is not JSON: %v; line = %q", err, scanner.Text())
		}
		responses = append(responses, response)
	}
	if err := scanner.Err(); err != nil {
		t.Fatalf("scan output: %v", err)
	}
	if len(responses) != 2 {
		t.Fatalf("len(responses) = %d, want 2; stdout = %q", len(responses), out.String())
	}
	if responses[0].JSONRPC != "2.0" || responses[1].JSONRPC != "2.0" {
		t.Fatalf("responses = %#v", responses)
	}
	if responses[0].ID != 1 || responses[1].ID != 2 {
		t.Fatalf("responses = %#v, want ids 1 and 2", responses)
	}
	if responses[0].Error != nil || responses[1].Error != nil {
		t.Fatalf("responses contain errors: %#v", responses)
	}
	var client engine.Client
	if err := json.Unmarshal(responses[1].Result, &client); err != nil {
		t.Fatalf("status result is not client JSON: %v; result = %s", err, responses[1].Result)
	}
	if client.MinecraftVersion != "1.21.6" {
		t.Fatalf("minecraftVersion = %q, want 1.21.6", client.MinecraftVersion)
	}
}

func TestDaemonStdioRejectsJSONOutputModeWithoutWritingStdout(t *testing.T) {
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine:  engine.NewMemory(),
		Stdin:   strings.NewReader("{not-json\n"),
		Stdout:  &out,
		Stderr:  &err,
		Version: "test",
	})
	root.SetArgs([]string{"--json", "daemon", "--stdio"})

	code := cli.Execute(root)
	if code != 2 {
		t.Fatalf("code = %d, want 2; stderr = %s stdout = %s", code, err.String(), out.String())
	}
	if out.String() != "" {
		t.Fatalf("stdout = %q, want empty JSON-RPC channel", out.String())
	}
	if !strings.Contains(err.String(), "cannot be used with daemon --stdio") {
		t.Fatalf("stderr = %q", err.String())
	}
}

func TestDaemonStdioGlobalOutputConflictDoesNotWriteCLIJSONToStdout(t *testing.T) {
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine:  engine.NewMemory(),
		Stdin:   strings.NewReader(""),
		Stdout:  &out,
		Stderr:  &err,
		Version: "test",
	})
	root.SetArgs([]string{"--json", "--plain", "daemon", "--stdio"})

	code := cli.Execute(root)
	if code != 2 {
		t.Fatalf("code = %d, want 2; stderr = %s stdout = %s", code, err.String(), out.String())
	}
	if out.String() != "" {
		t.Fatalf("stdout = %q, want empty JSON-RPC channel", out.String())
	}
	if err.String() == "" {
		t.Fatal("stderr = empty, want usage error")
	}
}

func TestDaemonStdioUnknownFlagDoesNotWriteCLIJSONToStdout(t *testing.T) {
	assertDaemonStdioFlagErrorKeepsStdoutEmpty(t, []string{"--json", "daemon", "--stdio", "--bogus"})
	assertDaemonStdioFlagErrorKeepsStdoutEmpty(t, []string{"--json", "daemon", "--bogus", "--stdio"})
}

func assertDaemonStdioFlagErrorKeepsStdoutEmpty(t *testing.T, args []string) {
	t.Helper()
	var out bytes.Buffer
	var err bytes.Buffer
	root := cli.NewRoot(cli.Dependencies{
		Engine:  engine.NewMemory(),
		Stdin:   strings.NewReader(""),
		Stdout:  &out,
		Stderr:  &err,
		Version: "test",
	})
	root.SetArgs(args)

	code := cli.Execute(root)
	if code != 2 {
		t.Fatalf("code = %d, want 2; stderr = %s stdout = %s", code, err.String(), out.String())
	}
	if out.String() != "" {
		t.Fatalf("stdout = %q, want empty JSON-RPC channel", out.String())
	}
	if err.String() == "" {
		t.Fatal("stderr = empty, want usage error")
	}
}
