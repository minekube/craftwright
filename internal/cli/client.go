package cli

import (
	"context"
	"fmt"
	"io"
	"time"

	"github.com/minekube/craftwright/internal/engine"
	"github.com/spf13/cobra"
)

func newClientCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "client",
		Short: "Manage Minecraft Java clients",
		RunE: func(cmd *cobra.Command, args []string) error {
			return cmd.Help()
		},
	}
	cmd.AddCommand(
		newClientLaunchCommand(deps, opts),
		newClientListCommand(deps, opts),
		newClientStatusCommand(deps, opts),
		newClientConnectCommand(deps, opts),
		newClientChatCommand(deps, opts),
		newClientWaitCommand(deps, opts),
		newClientLogsCommand(deps, opts),
		newClientStopCommand(deps, opts),
	)
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
			if mc == "" {
				return usageError("--mc is required")
			}
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
			if opts.Plain {
				return writeClientPlain(cmd.OutOrStdout(), client)
			}

			mode := "online"
			if client.Offline {
				mode = "offline"
			}
			if client.Server != "" {
				_, err = fmt.Fprintf(cmd.OutOrStdout(), "Launched client %s for Minecraft %s (%s) and connected to %s\n", client.Name, client.MinecraftVersion, mode, client.Server)
				return err
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "Launched client %s for Minecraft %s (%s)\n", client.Name, client.MinecraftVersion, mode)
			return err
		},
	}
	cmd.Flags().StringVar(&mc, "mc", "", "Minecraft version")
	cmd.Flags().StringVar(&loader, "loader", "fabric", "mod loader")
	cmd.Flags().BoolVar(&offline, "offline", true, "launch in offline mode")
	cmd.Flags().StringVar(&username, "username", "", "Minecraft username")
	cmd.Flags().StringVar(&server, "server", "", "server address to connect to")
	cmd.Flags().DurationVar(&timeout, "timeout", 2*time.Minute, "launch timeout")
	cmd.Flags().StringVar(&artifacts, "artifacts", "", "artifacts directory")
	return cmd
}

func newClientListCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List clients",
		Args:  cobra.NoArgs,
		RunE: func(cmd *cobra.Command, args []string) error {
			clients, err := deps.Engine.List(context.Background())
			if err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "clients": clients})
			}
			if opts.Plain {
				for _, client := range clients {
					if err := writeClientPlain(cmd.OutOrStdout(), client); err != nil {
						return err
					}
				}
				return nil
			}
			for _, client := range clients {
				if _, err := fmt.Fprintf(cmd.OutOrStdout(), "%s %s Minecraft %s (%s)\n", client.Name, client.State, client.MinecraftVersion, clientMode(client)); err != nil {
					return err
				}
			}
			return nil
		},
	}
	return cmd
}

func newClientStatusCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "status NAME",
		Short: "Show client status",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			client, err := deps.Engine.Status(context.Background(), args[0])
			if err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": client})
			}
			if opts.Plain {
				return writeClientPlain(cmd.OutOrStdout(), client)
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "%s is %s on Minecraft %s (%s)\n", client.Name, client.State, client.MinecraftVersion, clientMode(client))
			return err
		},
	}
	return cmd
}

func newClientConnectCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "connect NAME SERVER",
		Short: "Connect a client to a server",
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := deps.Engine.Connect(context.Background(), args[0], args[1]); err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": args[0], "server": args[1]})
			}
			if opts.Plain {
				_, err := fmt.Fprintf(cmd.OutOrStdout(), "%s %s\n", args[0], args[1])
				return err
			}
			_, err := fmt.Fprintf(cmd.OutOrStdout(), "Connected client %s to %s\n", args[0], args[1])
			return err
		},
	}
	return cmd
}

func newClientChatCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "chat NAME MESSAGE",
		Short: "Send chat from a client",
		Args:  cobra.ExactArgs(2),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := deps.Engine.Chat(context.Background(), args[0], args[1]); err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": args[0], "message": args[1]})
			}
			if opts.Plain {
				_, err := fmt.Fprintf(cmd.OutOrStdout(), "%s %s\n", args[0], args[1])
				return err
			}
			_, err := fmt.Fprintf(cmd.OutOrStdout(), "Sent chat from client %s: %s\n", args[0], args[1])
			return err
		},
	}
	return cmd
}

func newClientWaitCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	var chat string
	var timeout time.Duration

	cmd := &cobra.Command{
		Use:   "wait NAME",
		Short: "Wait for a client event",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if chat == "" {
				return usageError("--chat is required")
			}
			event, err := deps.Engine.Wait(context.Background(), engine.WaitRequest{
				Client:      args[0],
				ChatPattern: chat,
				Timeout:     timeout,
			})
			if err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "event": event})
			}
			if opts.Plain {
				_, err = fmt.Fprintf(cmd.OutOrStdout(), "%s %s %s\n", event.Type, event.Client, event.Message)
				return err
			}
			_, err = fmt.Fprintf(cmd.OutOrStdout(), "Observed %s for client %s: %s\n", event.Type, event.Client, event.Message)
			return err
		},
	}
	cmd.Flags().StringVar(&chat, "chat", "", "chat pattern to wait for")
	cmd.Flags().DurationVar(&timeout, "timeout", 5*time.Second, "wait timeout")
	return cmd
}

func newClientLogsCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	cmd := &cobra.Command{
		Use:   "logs NAME",
		Short: "Print client logs",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			logs, err := deps.Engine.Logs(context.Background(), args[0])
			if err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": args[0], "logs": logs})
			}
			for _, line := range logs {
				if _, err := fmt.Fprintln(cmd.OutOrStdout(), line); err != nil {
					return err
				}
			}
			return nil
		},
	}
	return cmd
}

func newClientStopCommand(deps Dependencies, opts *GlobalOptions) *cobra.Command {
	var force bool

	cmd := &cobra.Command{
		Use:   "stop NAME",
		Short: "Stop a client",
		Args:  cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if err := deps.Engine.Stop(context.Background(), args[0], force); err != nil {
				return err
			}
			if opts.JSON {
				return WriteJSON(cmd.OutOrStdout(), map[string]any{"ok": true, "client": args[0], "force": force})
			}
			if opts.Plain {
				_, err := fmt.Fprintf(cmd.OutOrStdout(), "%s stopped\n", args[0])
				return err
			}
			_, err := fmt.Fprintf(cmd.OutOrStdout(), "Stopped client %s\n", args[0])
			return err
		},
	}
	cmd.Flags().BoolVar(&force, "force", false, "force stop")
	return cmd
}

func writeClientPlain(w io.Writer, client engine.Client) error {
	_, err := fmt.Fprintf(w, "%s %s %s %s\n", client.Name, client.State, client.MinecraftVersion, clientMode(client))
	return err
}

func clientMode(client engine.Client) string {
	if client.Offline {
		return "offline"
	}
	return "online"
}
