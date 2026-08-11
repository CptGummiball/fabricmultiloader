# Security Policy

## Scope

FabricMultiLoader packages and selects mod code. Mods are arbitrary Java code that a user installs
deliberately, without a sandbox — no framework changes that. The security goal is therefore
bounded, and stated plainly:

> FabricMultiLoader must not introduce an attack surface that an ordinary Fabric mod does not
> already have, and it should detect when the shipped file is no longer the built file.

Full threat model: [docs/design/part-10-nfr.md](docs/design/part-10-nfr.md), chapter 39.

### In scope

* Path traversal or Zip Slip through manifest content (`OMNI-3004`)
* Loading a class the manifest should not be able to designate (`OMNI-2024`, `OMNI-2032`)
* Bypassing the payload integrity check (`OMNI-2013`)
* Denial of service through crafted manifests (parser limits, `OMNI-3003`)
* Insecure temporary file handling
* Leaking publishing tokens through build output
* The validator executing untrusted code at build time

### Out of scope

* A malicious mod doing malicious things. That is what mods can do by definition.
* The absence of cryptographic signatures. This is a documented decision, not an oversight: a
  signature scheme without key distribution, a trust anchor and revocation is cryptographically
  correct and semantically worthless. The `container.signatures` field is reserved for a future
  format version (chapter 39.9).
* Fabric Loader, Mixin, Minecraft or Gradle vulnerabilities. Report those to their maintainers.

## Reporting a vulnerability

**Do not open a public issue.**

Use GitHub's private vulnerability reporting:
<https://github.com/CptGummiball/fabricmultiloader/security/advisories/new>

If that is unavailable, email **treeman1992@outlook.de** with `SECURITY` in the subject.

Please include: affected version, an outline of the impact, reproduction steps or a proof of
concept, and whether the issue is already public.

## What to expect

| Stage | Commitment |
|---|---|
| Acknowledgement | within 7 days |
| Initial assessment (severity, affected versions) | within 14 days |
| Fix or a documented mitigation | depends on severity; a target date is agreed with you |
| Credit | named in the advisory and the changelog, unless you prefer otherwise |

Security fixes are released as patch releases on every affected minor line. The previous major
version receives security fixes for 12 months after the next major is released (chapter 42.6).

## Current status

The project is in the design and early implementation phase. There is no released artifact yet, so
there is nothing deployed to attack. Reports about the design itself are welcome and will be
treated the same way — a flaw found now is far cheaper than one found after release.
