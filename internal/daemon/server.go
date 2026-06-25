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
	ID      json.RawMessage `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params"`
}

type response struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id"`
	Result  any             `json:"result,omitempty"`
	Error   *rpcError       `json:"error,omitempty"`
}

type rpcError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

// Serve runs a newline-delimited JSON-RPC 2.0 server over in and out.
func Serve(ctx context.Context, eng engine.Engine, in io.Reader, out io.Writer) error {
	scanner := bufio.NewScanner(in)
	scanner.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	encoder := json.NewEncoder(out)
	for scanner.Scan() {
		if err := ctx.Err(); err != nil {
			return err
		}

		var req request
		if err := json.Unmarshal(scanner.Bytes(), &req); err != nil {
			resp := response{
				JSONRPC: "2.0",
				ID:      json.RawMessage("null"),
				Error:   newRPCError(-32700, err),
			}
			if err := encoder.Encode(resp); err != nil {
				return err
			}
			continue
		}

		resp := handle(ctx, eng, req)
		if err := encoder.Encode(resp); err != nil {
			return err
		}
	}
	if err := scanner.Err(); err != nil {
		return err
	}
	return ctx.Err()
}

func handle(ctx context.Context, eng engine.Engine, req request) response {
	resp := response{JSONRPC: "2.0", ID: req.ID}
	switch req.Method {
	case "client.launch":
		var params engine.LaunchRequest
		if err := json.Unmarshal(req.Params, &params); err != nil {
			resp.Error = newRPCError(-32602, err)
			return resp
		}
		client, err := eng.Launch(ctx, params)
		if err != nil {
			resp.Error = newRPCError(-32000, err)
			return resp
		}
		resp.Result = client
		return resp
	case "client.status":
		var params struct {
			Name string `json:"name"`
		}
		if err := json.Unmarshal(req.Params, &params); err != nil {
			resp.Error = newRPCError(-32602, err)
			return resp
		}
		client, err := eng.Status(ctx, params.Name)
		if err != nil {
			resp.Error = newRPCError(-32000, err)
			return resp
		}
		resp.Result = client
		return resp
	default:
		resp.Error = &rpcError{Code: -32601, Message: fmt.Sprintf("method not found: %s", req.Method)}
		return resp
	}
}

func newRPCError(code int, err error) *rpcError {
	return &rpcError{Code: code, Message: err.Error()}
}
