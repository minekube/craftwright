package scenario

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"os"
	"time"

	"github.com/minekube/craftwright/internal/engine"
	"gopkg.in/yaml.v3"
)

type File struct {
	Version int               `yaml:"version"`
	Clients map[string]Client `yaml:"clients"`
	Steps   []Step            `yaml:"steps"`
}

type Client struct {
	MC      string `yaml:"mc"`
	Loader  string `yaml:"loader"`
	Offline bool   `yaml:"offline"`
}

type Step struct {
	Launch  *string      `yaml:"launch"`
	Connect *ConnectStep `yaml:"connect"`
	Chat    *ChatStep    `yaml:"chat"`
	Wait    *WaitStep    `yaml:"wait"`

	launchSet  bool
	connectSet bool
	chatSet    bool
	waitSet    bool
}

func (s *Step) UnmarshalYAML(value *yaml.Node) error {
	if value.Kind != yaml.MappingNode {
		return fmt.Errorf("step must be a mapping")
	}
	seen := make(map[string]struct{}, len(value.Content)/2)
	for i := 0; i < len(value.Content); i += 2 {
		key := value.Content[i]
		val := value.Content[i+1]
		if _, ok := seen[key.Value]; ok {
			return fmt.Errorf("line %d: duplicate field %s in type scenario.Step", key.Line, key.Value)
		}
		seen[key.Value] = struct{}{}
		switch key.Value {
		case "launch":
			s.launchSet = true
			if !isNullYAML(val) {
				var launch string
				if err := val.Decode(&launch); err != nil {
					return err
				}
				s.Launch = &launch
			}
		case "connect":
			s.connectSet = true
			if !isNullYAML(val) {
				var connect ConnectStep
				if err := val.Decode(&connect); err != nil {
					return err
				}
				s.Connect = &connect
			}
		case "chat":
			s.chatSet = true
			if !isNullYAML(val) {
				var chat ChatStep
				if err := val.Decode(&chat); err != nil {
					return err
				}
				s.Chat = &chat
			}
		case "wait":
			s.waitSet = true
			if !isNullYAML(val) {
				var wait WaitStep
				if err := val.Decode(&wait); err != nil {
					return err
				}
				s.Wait = &wait
			}
		default:
			return fmt.Errorf("line %d: field %s not found in type scenario.Step", key.Line, key.Value)
		}
	}
	return nil
}

type ConnectStep struct {
	Client string `yaml:"client"`
	Server string `yaml:"server"`
}

func (s *ConnectStep) UnmarshalYAML(value *yaml.Node) error {
	return decodeKnownStringFields(value, "scenario.ConnectStep", map[string]*string{
		"client": &s.Client,
		"server": &s.Server,
	})
}

type ChatStep struct {
	Client  string `yaml:"client"`
	Message string `yaml:"message"`
}

func (s *ChatStep) UnmarshalYAML(value *yaml.Node) error {
	return decodeKnownStringFields(value, "scenario.ChatStep", map[string]*string{
		"client":  &s.Client,
		"message": &s.Message,
	})
}

type WaitStep struct {
	Client  string `yaml:"client"`
	Chat    string `yaml:"chat"`
	Timeout string `yaml:"timeout"`
}

func (s *WaitStep) UnmarshalYAML(value *yaml.Node) error {
	return decodeKnownStringFields(value, "scenario.WaitStep", map[string]*string{
		"client":  &s.Client,
		"chat":    &s.Chat,
		"timeout": &s.Timeout,
	})
}

type Result struct {
	OK    bool `json:"ok"`
	Steps int  `json:"steps"`
}

func ValidateFile(path string) (Result, error) {
	file, err := loadFile(path)
	if err != nil {
		return Result{}, err
	}
	if err := validate(file); err != nil {
		return Result{}, err
	}
	return Result{OK: true, Steps: len(file.Steps)}, nil
}

func RunFile(ctx context.Context, eng engine.Engine, path string) (Result, error) {
	file, err := loadFile(path)
	if err != nil {
		return Result{}, err
	}
	if err := validate(file); err != nil {
		return Result{}, err
	}

	var steps int
	var launched []string
	for i, step := range file.Steps {
		number := i + 1
		switch {
		case step.Launch != nil:
			name := *step.Launch
			client := file.Clients[name]
			if _, err := eng.Launch(ctx, engine.LaunchRequest{
				Name:             name,
				MinecraftVersion: client.MC,
				Loader:           client.Loader,
				Offline:          client.Offline,
			}); err != nil {
				stopLaunched(eng, launched)
				return Result{}, fmt.Errorf("step %d launch: %w", number, err)
			}
			launched = append(launched, name)
		case step.Connect != nil:
			if err := eng.Connect(ctx, step.Connect.Client, step.Connect.Server); err != nil {
				stopLaunched(eng, launched)
				return Result{}, fmt.Errorf("step %d connect: %w", number, err)
			}
		case step.Chat != nil:
			if err := eng.Chat(ctx, step.Chat.Client, step.Chat.Message); err != nil {
				stopLaunched(eng, launched)
				return Result{}, fmt.Errorf("step %d chat: %w", number, err)
			}
		case step.Wait != nil:
			timeout, err := waitTimeout(*step.Wait)
			if err != nil {
				stopLaunched(eng, launched)
				return Result{}, fmt.Errorf("step %d wait: %w", number, err)
			}
			if _, err := eng.Wait(ctx, engine.WaitRequest{
				Client:      step.Wait.Client,
				ChatPattern: step.Wait.Chat,
				Timeout:     timeout,
			}); err != nil {
				stopLaunched(eng, launched)
				return Result{}, fmt.Errorf("step %d wait: %w", number, err)
			}
		default:
			return Result{}, fmt.Errorf("step %d: empty step", number)
		}
		steps++
	}

	return Result{OK: true, Steps: steps}, nil
}

func stopLaunched(eng engine.Engine, launched []string) {
	cleanupCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	for i := len(launched) - 1; i >= 0; i-- {
		_ = eng.Stop(cleanupCtx, launched[i], true)
	}
}

func loadFile(path string) (File, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return File{}, err
	}
	var file File
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	if err := decoder.Decode(&file); err != nil {
		return File{}, err
	}
	var extra yaml.Node
	if err := decoder.Decode(&extra); err != io.EOF {
		if err != nil {
			return File{}, err
		}
		return File{}, fmt.Errorf("multiple YAML documents are not supported")
	}
	return file, nil
}

func validate(file File) error {
	if file.Version != 1 {
		return fmt.Errorf("version %d is not supported", file.Version)
	}
	for i, step := range file.Steps {
		if err := validateStep(file, i+1, step); err != nil {
			return err
		}
	}
	return nil
}

func validateStep(file File, number int, step Step) error {
	actions := 0
	if step.launchSet {
		actions++
	}
	if step.connectSet {
		actions++
	}
	if step.chatSet {
		actions++
	}
	if step.waitSet {
		actions++
	}
	if actions != 1 {
		return fmt.Errorf("step %d must contain exactly one action", number)
	}

	switch {
	case step.launchSet:
		if step.Launch == nil {
			return validateClientReference(file, number, "launch", "")
		}
		if err := validateClientReference(file, number, "launch", *step.Launch); err != nil {
			return err
		}
		return validateLaunchClient(file.Clients[*step.Launch], number, *step.Launch)
	case step.connectSet:
		if step.Connect == nil {
			return validateClientReference(file, number, "connect.client", "")
		}
		if err := validateClientReference(file, number, "connect.client", step.Connect.Client); err != nil {
			return err
		}
		if step.Connect.Server == "" {
			return fmt.Errorf("step %d connect.server is required", number)
		}
	case step.chatSet:
		if step.Chat == nil {
			return validateClientReference(file, number, "chat.client", "")
		}
		if err := validateClientReference(file, number, "chat.client", step.Chat.Client); err != nil {
			return err
		}
		if step.Chat.Message == "" {
			return fmt.Errorf("step %d chat.message is required", number)
		}
	case step.waitSet:
		if step.Wait == nil {
			return validateClientReference(file, number, "wait.client", "")
		}
		if err := validateClientReference(file, number, "wait.client", step.Wait.Client); err != nil {
			return err
		}
		if step.Wait.Chat == "" {
			return fmt.Errorf("step %d wait.chat is required", number)
		}
		if _, err := waitTimeout(*step.Wait); err != nil {
			return fmt.Errorf("step %d wait: %w", number, err)
		}
	}
	return nil
}

func validateLaunchClient(client Client, number int, name string) error {
	if client.MC == "" {
		return fmt.Errorf("step %d launch client %s mc is required", number, name)
	}
	if client.Loader == "" {
		return fmt.Errorf("step %d launch client %s loader is required", number, name)
	}
	return nil
}

func validateClientReference(file File, number int, field string, name string) error {
	if name == "" {
		return fmt.Errorf("step %d %s is required", number, field)
	}
	if _, ok := file.Clients[name]; !ok {
		return fmt.Errorf("step %d %s references undefined client %s", number, field, name)
	}
	return nil
}

func isNullYAML(node *yaml.Node) bool {
	return node.Kind == yaml.ScalarNode && node.Tag == "!!null"
}

func decodeKnownStringFields(value *yaml.Node, typeName string, fields map[string]*string) error {
	if value.Kind != yaml.MappingNode {
		return fmt.Errorf("%s must be a mapping", typeName)
	}
	seen := make(map[string]struct{}, len(value.Content)/2)
	for i := 0; i < len(value.Content); i += 2 {
		key := value.Content[i]
		if _, ok := seen[key.Value]; ok {
			return fmt.Errorf("line %d: duplicate field %s in type %s", key.Line, key.Value, typeName)
		}
		seen[key.Value] = struct{}{}
		field, ok := fields[key.Value]
		if !ok {
			return fmt.Errorf("line %d: field %s not found in type %s", key.Line, key.Value, typeName)
		}
		if isNullYAML(value.Content[i+1]) {
			*field = ""
			continue
		}
		if err := value.Content[i+1].Decode(field); err != nil {
			return err
		}
	}
	return nil
}

func waitTimeout(step WaitStep) (time.Duration, error) {
	if step.Timeout == "" {
		return 30 * time.Second, nil
	}
	return time.ParseDuration(step.Timeout)
}
