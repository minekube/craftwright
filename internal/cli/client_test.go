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
		Engine:  e,
		Stdout:  &out,
		Stderr:  &err,
		Version: "test",
	})
	root.SetArgs(args)
	code = cli.Execute(root)
	return out.String(), err.String(), code
}

func TestClientLaunchJSON(t *testing.T) {
	stdout, stderr, code := execute("--json", "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	var payload struct {
		OK     bool `json:"ok"`
		Client struct {
			Name             string `json:"name"`
			State            string `json:"state"`
			MinecraftVersion string `json:"minecraftVersion"`
			Loader           string `json:"loader"`
			Offline          bool   `json:"offline"`
		} `json:"client"`
	}
	if err := json.Unmarshal([]byte(stdout), &payload); err != nil {
		t.Fatalf("stdout is not JSON: %v; stdout = %q", err, stdout)
	}
	if !payload.OK {
		t.Fatalf("payload = %#v", payload)
	}
	if payload.Client.Name != "alice" || payload.Client.State != "running" || payload.Client.MinecraftVersion != "1.21.6" || payload.Client.Loader != "fabric" || !payload.Client.Offline {
		t.Fatalf("client payload = %#v", payload.Client)
	}
}

func TestClientLaunchConnectLifecycle(t *testing.T) {
	e := engine.NewMemory()

	stdout, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--loader", "fabric", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}

	stdout, stderr, code = executeWithEngine(e, "client", "connect", "alice", "localhost:25565")
	if code != 0 {
		t.Fatalf("connect code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "Connected client alice to localhost:25565") {
		t.Fatalf("stdout = %q", stdout)
	}
}

func TestClientListAndStatusPlain(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}

	stdout, stderr, code := executeWithEngine(e, "--plain", "client", "list")
	if code != 0 {
		t.Fatalf("list code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if stdout != "alice running 1.21.6 offline\n" {
		t.Fatalf("list stdout = %q", stdout)
	}

	stdout, stderr, code = executeWithEngine(e, "--plain", "client", "status", "alice")
	if code != 0 {
		t.Fatalf("status code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if stdout != "alice running 1.21.6 offline\n" {
		t.Fatalf("status stdout = %q", stdout)
	}
}

func TestClientChatThenWaitPlain(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}
	_, stderr, code = executeWithEngine(e, "client", "chat", "alice", "Welcome alice")
	if code != 0 {
		t.Fatalf("chat code = %d stderr = %s", code, stderr)
	}

	stdout, stderr, code := executeWithEngine(e, "--plain", "client", "wait", "alice", "--chat", "/Welcome/")
	if code != 0 {
		t.Fatalf("wait code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if stdout != "client.chat alice Welcome alice\n" {
		t.Fatalf("wait stdout = %q", stdout)
	}
}

func TestClientWaitRequiresChatPattern(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}

	_, stderr, code = executeWithEngine(e, "client", "wait", "alice")
	if code != 2 {
		t.Fatalf("code = %d stderr = %s", code, stderr)
	}
	if !strings.Contains(stderr, "--chat is required") {
		t.Fatalf("stderr = %q", stderr)
	}
}

func TestClientLogsPlain(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}
	_, stderr, code = executeWithEngine(e, "client", "chat", "alice", "hello")
	if code != 0 {
		t.Fatalf("chat code = %d stderr = %s", code, stderr)
	}

	stdout, stderr, code := executeWithEngine(e, "--plain", "client", "logs", "alice")
	if code != 0 {
		t.Fatalf("logs code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if stdout != "LAUNCH 1.21.6 fabric\nCHAT hello\n" {
		t.Fatalf("logs stdout = %q", stdout)
	}
}

func TestClientStop(t *testing.T) {
	e := engine.NewMemory()
	_, stderr, code := executeWithEngine(e, "client", "launch", "alice", "--mc", "1.21.6", "--offline")
	if code != 0 {
		t.Fatalf("launch code = %d stderr = %s", code, stderr)
	}

	stdout, stderr, code := executeWithEngine(e, "client", "stop", "alice")
	if code != 0 {
		t.Fatalf("stop code = %d stderr = %s stdout = %s", code, stderr, stdout)
	}
	if !strings.Contains(stdout, "Stopped client alice") {
		t.Fatalf("stop stdout = %q", stdout)
	}
}
