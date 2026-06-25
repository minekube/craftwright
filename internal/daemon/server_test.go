package daemon_test

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"strings"
	"testing"

	"github.com/minekube/craftwright/internal/daemon"
	"github.com/minekube/craftwright/internal/engine"
)

type rpcResponse struct {
	JSONRPC string           `json:"jsonrpc"`
	ID      json.RawMessage  `json:"id"`
	Result  json.RawMessage  `json:"result,omitempty"`
	Error   *rpcErrorPayload `json:"error,omitempty"`
}

type rpcErrorPayload struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

func TestServeLaunchAndStatus(t *testing.T) {
	input := strings.NewReader(
		`{"jsonrpc":"2.0","id":1,"method":"client.launch","params":{"name":"alice","minecraftVersion":"1.21.6","loader":"fabric","offline":true}}` + "\n" +
			`{"jsonrpc":"2.0","id":2,"method":"client.status","params":{"name":"alice"}}` + "\n",
	)
	var output bytes.Buffer

	if err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output); err != nil {
		t.Fatalf("Serve returned error: %v", err)
	}

	responses := decodeRPCResponses(t, output.String())
	if len(responses) != 2 {
		t.Fatalf("len(responses) = %d, want 2; output = %q", len(responses), output.String())
	}
	assertSuccessClient(t, responses[0], "1")
	assertSuccessClient(t, responses[1], "2")
}

func TestServeUnknownMethodReturnsRPCError(t *testing.T) {
	input := strings.NewReader(`{"jsonrpc":"2.0","id":"abc","method":"client.missing","params":{}}` + "\n")
	var output bytes.Buffer

	if err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output); err != nil {
		t.Fatalf("Serve returned error: %v", err)
	}

	responses := decodeRPCResponses(t, output.String())
	if len(responses) != 1 {
		t.Fatalf("len(responses) = %d, want 1; output = %q", len(responses), output.String())
	}
	assertRPCError(t, responses[0], `"abc"`, -32601)
}

func TestServeEngineErrorReturnsRPCError(t *testing.T) {
	input := strings.NewReader(`{"jsonrpc":"2.0","id":7,"method":"client.status","params":{"name":"missing"}}` + "\n")
	var output bytes.Buffer

	if err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output); err != nil {
		t.Fatalf("Serve returned error: %v", err)
	}

	responses := decodeRPCResponses(t, output.String())
	if len(responses) != 1 {
		t.Fatalf("len(responses) = %d, want 1; output = %q", len(responses), output.String())
	}
	assertRPCError(t, responses[0], "7", -32000)
}

func TestServeAcceptsRequestsAboveDefaultScannerLimit(t *testing.T) {
	input := strings.NewReader(`{"jsonrpc":"2.0","id":11,"method":"client.status","params":{"name":"` + strings.Repeat("a", 256*1024) + `"}}` + "\n")
	var output bytes.Buffer

	if err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output); err != nil {
		t.Fatalf("Serve returned error: %v", err)
	}

	responses := decodeRPCResponses(t, output.String())
	if len(responses) != 1 {
		t.Fatalf("len(responses) = %d, want 1; output = %q", len(responses), output.String())
	}
	assertRPCError(t, responses[0], "11", -32000)
}

func TestServeParseErrorReturnsRPCErrorAndContinues(t *testing.T) {
	input := strings.NewReader("{not-json\n" +
		`{"jsonrpc":"2.0","id":2,"method":"client.status","params":{"name":"missing"}}` + "\n")
	var output bytes.Buffer

	if err := daemon.Serve(context.Background(), engine.NewMemory(), input, &output); err != nil {
		t.Fatalf("Serve returned error: %v", err)
	}

	responses := decodeRPCResponses(t, output.String())
	if len(responses) != 2 {
		t.Fatalf("len(responses) = %d output = %q", len(responses), output.String())
	}
	assertRPCError(t, responses[0], "null", -32700)
	assertRPCError(t, responses[1], "2", -32000)
}

func decodeRPCResponses(t *testing.T, output string) []rpcResponse {
	t.Helper()
	var responses []rpcResponse
	scanner := bufio.NewScanner(strings.NewReader(output))
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for scanner.Scan() {
		var response rpcResponse
		if err := json.Unmarshal(scanner.Bytes(), &response); err != nil {
			t.Fatalf("response is not JSON: %v; line = %q", err, scanner.Text())
		}
		responses = append(responses, response)
	}
	if err := scanner.Err(); err != nil {
		t.Fatalf("scan output: %v", err)
	}
	return responses
}

func assertSuccessClient(t *testing.T, response rpcResponse, id string) {
	t.Helper()
	if response.JSONRPC != "2.0" {
		t.Fatalf("jsonrpc = %q, want 2.0", response.JSONRPC)
	}
	if string(response.ID) != id {
		t.Fatalf("id = %s, want %s", response.ID, id)
	}
	if response.Error != nil {
		t.Fatalf("error = %#v, want nil", response.Error)
	}
	if len(response.Result) == 0 {
		t.Fatal("result is empty")
	}
	var client engine.Client
	if err := json.Unmarshal(response.Result, &client); err != nil {
		t.Fatalf("result is not client JSON: %v; result = %s", err, response.Result)
	}
	if client.Name != "alice" || client.MinecraftVersion != "1.21.6" || client.Loader != "fabric" || !client.Offline {
		t.Fatalf("client = %#v", client)
	}
}

func assertRPCError(t *testing.T, response rpcResponse, id string, code int) {
	t.Helper()
	if response.JSONRPC != "2.0" {
		t.Fatalf("jsonrpc = %q, want 2.0", response.JSONRPC)
	}
	if string(response.ID) != id {
		t.Fatalf("id = %s, want %s", response.ID, id)
	}
	if len(response.Result) != 0 {
		t.Fatalf("result = %s, want omitted", response.Result)
	}
	if response.Error == nil {
		t.Fatal("error is nil")
	}
	if response.Error.Code != code {
		t.Fatalf("error code = %d, want %d; error = %#v", response.Error.Code, code, response.Error)
	}
	if response.Error.Message == "" {
		t.Fatal("error message is empty")
	}
}
