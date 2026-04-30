---
name: verify-fix
description: Walk through reproducing a bug, applying a fix, and confirming it works. Use when you have a fix to validate before committing.
allowed-tools: Bash Read Grep
---

# Verify Fix Workflow

Guide the user through bug verification. Follow these steps:

## 1. Understand the bug
Ask: file path(s) affected, steps to reproduce in-game or in tests, expected vs actual behavior.

## 2. Locate the code
Use Read/Grep to find the affected code. Print the relevant section.

## 3. Review the fix
User provides the fix (code snippet, diff, or explanation).
- Check: does it address the root cause or just the symptom?
- Check for PaperMC-specific pitfalls: async thread access, entity lifecycle, scheduler misuse
- Flag any side effects

## 4. Build and test
```bash
./gradlew compileJava -q && ./gradlew test -q
```
Report: Compilation PASS/FAIL, Tests PASS/FAIL, any new failures.

## 5. Confirm
All green → "Ready to commit?"
Failed → "Try a different approach?"
