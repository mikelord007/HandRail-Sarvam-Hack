# Handrail

Voice-first GUI agent for Indian languages. Android, Kotlin, native. Built during a ~30-hour hackathon.

## Modes

- **Narration**: capture the current screen, summarize it via an LLM, speak the summary aloud in the user's language.
- **Takeover**: speak a task, and the agent perceives the screen, picks the next action, executes it, narrates each step, and hands control back before any payment/send/submit action.

## Credits

The perception layer (`app/src/main/java/com/handrail/perception/`) is adapted from
[MobileClaw](https://github.com/ChenKuanSun/MobileClaw)'s `ClawAccessibilityService.kt`,
Copyright (c) 2026 CK Sun, licensed under the MIT License:

```
MIT License

Copyright (c) 2026 CK Sun

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
