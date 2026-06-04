"""
Kokoro TTS — Modal serverless endpoint

Deploy:  modal deploy scripts/modal_tts.py
Test:    curl -X POST <url> -H "Content-Type: application/json" \
              -d '{"text":"Hello world","voice":"af_sky"}' \
              --output test.wav

The endpoint returns raw WAV bytes.
Scales to zero after 5 min idle; cold start ~15s on first request.
"""

import io
import modal
import numpy as np

app = modal.App("podcast-kokoro-tts")

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "kokoro>=0.9.4",
        "soundfile",
        "numpy",
        "scipy",
        "fastapi[standard]",
    )
)


@app.cls(
    image=image,
    gpu="A10G",
    scaledown_window=300,   # shut down after 5 min idle
    min_containers=0,        # scale to zero
)
class KokoroTTS:
    @modal.enter()
    def load(self):
        from kokoro import KPipeline
        self.pipeline = KPipeline(lang_code="a")  # 'a' = American English

    @modal.fastapi_endpoint(method="POST")
    def synthesize(self, body: dict) -> "fastapi.responses.Response":
        import soundfile as sf
        from fastapi.responses import Response

        text: str = body.get("text", "")
        voice: str = body.get("voice", "af_sky")

        if not text:
            from fastapi import HTTPException
            raise HTTPException(status_code=400, detail="text is required")

        chunks = []
        for _, _, audio in self.pipeline(text, voice=voice):
            chunks.append(audio)

        combined = np.concatenate(chunks)
        buf = io.BytesIO()
        sf.write(buf, combined, 24000, format="WAV")

        return Response(content=buf.getvalue(), media_type="audio/wav")
