# Public Agent Operational Workflow Design

Date: 2026-06-28

## Problem

The repo-local public gameplay skill explains the generated API rule, but it
does not yet give an external agent enough concrete workflow to operate
Craftless through the same surfaces required by the completion gate:
supervisor OpenAPI, per-client OpenAPI, adaptive CLI discovery, JSON-RPC-style
queries, SSE streams, generated invocation, evidence artifacts, and blocker
reporting.

That gap can push future agents back toward guessing schemas, inventing static
commands, or treating accepted action calls as gameplay proof.

## Goal

Make `.agents/skills/craftless-public-gameplay-agent/SKILL.md` operational
enough that a future agent can use Craftless as an external API/CLI user
without scenario shortcuts.

## Requirements

- The skill must keep generated per-client OpenAPI as the source of truth.
- The skill must describe adaptive CLI usage as a consumer of the live spec,
  not as a static gameplay command catalog.
- The skill must explain JSON-RPC-style query/control through HTTP POST and
  SSE as the observation stream.
- The skill must tell agents to preserve evidence artifacts for OpenAPI,
  actions/resources, SSE, action logs, state logs, and final state.
- The skill must require agents to report missing generic primitives as
  blockers instead of inventing scenario actions.
- The skill must keep the no-server-provisioning, no-manual-movement, and
  no-static-shortcut final gameplay rules.
- A repository policy test must fail if the skill loses the key operational
  guidance for adaptive CLI, JSON-RPC-style control, SSE, generated
  invocation, and blocker reporting.
- This phase must not add public gameplay actions, generated route families,
  CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts,
  new compiled lanes, public version-specific APIs, or new Minecraft support
  claims.

## Non-Goals

- No product code behavior changes.
- No new Minecraft actions or aliases.
- No final gameplay rerun.
- No new release.

## Verification

- `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.public gameplay agent skill keeps generated workflow guidance'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
