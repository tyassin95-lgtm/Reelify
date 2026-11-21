# Creative quality evaluation

30 scenarios (`app/src/test/java/app/reelify/eval/CreativeEvalHarness.kt`) cover all 8 treatment
profiles, all 6 templates, 30/45/60 s, 17 Iraqi Arabic + 13 English.

**Status: not yet run against the live API** — the build environment has no OpenAI key. No scores
below have been filled in; nothing here is a human-review result.

## How to run

```bash
REELIFY_OPENAI_API_KEY=sk-... ./gradlew :app:testDebugUnitTest --tests 'app.reelify.eval.*'
# optional: REELIFY_OPENAI_MODEL=<id>
# → app/build/reports/creative-eval.md (all hooks, scripts, word counts, formal-marker hits,
#   hook similarity, validator notes, and an empty scoring table per scenario)
```

## Rubric (score 1–5 by a human, ideally a native Iraqi speaker for `ar-IQ`)

| Criterion | 5 means |
|---|---|
| Relevance | Every shot serves the topic and the template role |
| Originality | Not generic; differs from previous reels / recent hooks |
| Hook | Stops the scroll in <3 s; the three hooks use genuinely different angles |
| Naturalness | Sounds like this clinician speaking (Iraqi: spoken dialect, not MSA/Gulf/Levantine, not slang-heavy) |
| Accuracy | Only facts from the approved profile; terminology spelled as preferred |
| Caution | No guarantees/permanence/"safe for everyone"; assessment mentioned where it matters |
| Timing | Each shot speakable in its 10/15/20 s at a natural pace |
| Repetition | No repeated phrases across shots or across scenarios |
| Clarity | One idea per sentence, clear payoff |

Record approved/corrected lines in *Studio → Iraqi style guide*; they are fed back into every Iraqi
prompt as owner-approved references.
