package cli

import (
	"context"
	"fmt"
	"io"
	"os"

	"github.com/minekube/craftwright/internal/daemon"
	"github.com/spf13/cobra"
)

func newDaemonCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	var stdio bool

	cmd := &cobra.Command{
		Use:   "daemon",
		Short: "Run the Craftwright daemon",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			if !stdio {
				return cmd.Help()
			}
			if opts.JSON || opts.JSONL || opts.Plain {
				return daemonStdioUsageError("--json, --jsonl, and --plain cannot be used with daemon --stdio")
			}
			return daemon.Serve(context.Background(), deps.Engine, daemonInput(deps.Stdin), cmd.OutOrStdout())
		},
	}
	cmd.Flags().BoolVar(&stdio, "stdio", false, "serve JSON-RPC over stdin/stdout")
	cmd.SetFlagErrorFunc(func(cmd *cobra.Command, err error) error {
		return daemonStdioUsageError("%v", err)
	})
	return cmd
}

func daemonInput(stdin io.Reader) io.Reader {
	if stdin != nil {
		return stdin
	}
	return os.Stdin
}

func isDaemonStdioCommand(cmd *cobra.Command) bool {
	flag := cmd.Flags().Lookup("stdio")
	return cmd.Name() == "daemon" && flag != nil && flag.Value.String() == "true"
}

type daemonStdioError struct {
	err error
}

func (e daemonStdioError) Error() string {
	return e.err.Error()
}

func (e daemonStdioError) Unwrap() error {
	return e.err
}

func (e daemonStdioError) suppressDaemonStdioEnvelope() {}

func daemonStdioUsageError(format string, args ...any) error {
	return appError{
		Code: 2,
		Err: daemonStdioError{
			err: fmt.Errorf("%w: %s", ErrInvalidUsage, fmt.Sprintf(format, args...)),
		},
	}
}
