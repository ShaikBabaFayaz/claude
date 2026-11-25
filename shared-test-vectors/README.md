# Shared test vectors

Single source of truth for the ISBN and email validation rules, read by **both** test suites so the client and
server rules cannot drift apart (architecture 10.6, risk R-4, plan decision PD-1):

| Consumer | Rule implementation | How the files are read |
|---|---|---|
| Backend (`backend/`) | `IsbnValidator`, `IsbnNormalizer`, `LibraryEmailValidator` | `pom.xml` copies this folder onto the test classpath as `/shared-test-vectors/*.json` |
| Frontend (`frontend/`) | `src/lib/validators.ts` | imported by relative path `../../shared-test-vectors/*.json` |

Do not edit a vector in only one place: change the JSON here and make both suites pass. The server stays authoritative.

## Files and format

Both files are UTF-8 JSON objects with the same top-level shape. `formatVersion` (currently `1`) changes only if the
shape changes. `note` is free text for humans and must be ignored by tests (it may be used in a test display name).

### `isbn.json`

```json
{
  "formatVersion": 1,
  "valid":   [ { "input": "0-306-40615-2", "normalized": "0306406152", "note": "..." } ],
  "invalid": [ { "input": "0306406153", "note": "..." } ],
  "notEquivalent": [ { "a": "0306406152", "b": "9780306406157", "note": "..." } ]
}
```

- `valid[].input` is a raw string as a user might type it. A validator must accept it, and the normaliser must turn
  it into `normalized`.
- `invalid[].input` must be rejected by the validator.
- `notEquivalent[]`: `a` and `b` are both valid ISBNs and must still be **different** after normalisation. An
  ISBN-10 is never converted to ISBN-13 (decision Q-A1 / ADR-22).

ISBN rules (FR-5, AC-1.BE.3): normalising removes every hyphen (`-`) and space (` `) and upper-cases a trailing `x`.
The result is valid if it is 10 characters (9 ASCII digits followed by an ASCII digit or `X`, weighted mod-11 sum
`10*d1 + 9*d2 + ... + 1*d10 = 0 mod 11`, with `X` = 10) or 13 ASCII digits (weights 1 and 3 alternating, sum is
`0 mod 10`). Only ASCII digits `0-9` count as digits (a full-width digit is invalid).

### `email.json`

```json
{
  "formatVersion": 1,
  "valid":   [ { "input": "ada@example.org", "note": "..." } ],
  "invalid": [ { "input": "ada@example", "note": "..." } ]
}
```

Inputs are given **already trimmed** (request records and the UI trim before validating), and the vectors contain no
leading/trailing whitespace on purpose. A validator must accept every `valid[].input` and reject every
`invalid[].input`.

Email rule (FR-11, AC-3.BE.2): at most 254 characters; no whitespace; exactly one `@`; a non-empty local part; a
domain that contains at least one dot and has no empty label (so `a@.org`, `a@b.` and `a@b..org` are invalid).
Case is preserved; lower-casing for the uniqueness key is a separate step (`Locale.ROOT` on the backend, DR-15).

## Regenerating

The files are hand-maintained data. Keep the check digits correct when adding an ISBN: both suites will fail loudly if
a `valid` vector is not actually valid.
