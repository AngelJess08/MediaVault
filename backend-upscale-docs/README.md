# Documentación del Backend de Escalado de Video IA

Esta guía detalla las tres opciones disponibles para procesar el escalado de videos en MediaVault.

## Opción A — Replicate.com (Recomendada para iniciar)
Es la forma más sencilla de empezar sin mantener infraestructura.

1. Entra a [Replicate.com](https://replicate.com/) y crea una cuenta con GitHub.
2. Ve a los ajustes de cuenta (API tokens) y crea una nueva **API Key**.
3. En MediaVault, ve a `Ajustes -> Escalado IA -> Proveedor`, selecciona `Replicate` y pega tu llave.

**Modelos sugeridos:**
- `lucataco/real-esrgan-video`
- `nightmareai/real-esrgan`

**Costos:** ~ $0.05 a $0.20 por minuto de video procesado. Replicate regala aproximadamente $5 de créditos gratis.

---

## Opción B — fal.ai (Alto rendimiento)
Fal.ai ofrece tiempos de inicio en frío mucho más rápidos.

1. Entra a [fal.ai](https://fal.ai/) y regístrate.
2. Consigue tu **Key** en el dashboard de developers.
3. En MediaVault, elige `fal.ai` como proveedor.

**Modelos:** `fal-ai/video-upscaler`
**Créditos:** Al registrarte recibes ~$1-5 USD en créditos.

---

## Opción C — Backend propio con FastAPI (Usuarios Avanzados)
Si tienes acceso a un servidor con GPUs NVIDIA (Vast.ai, RunPod) puedes alojar el backend para evitar cobros por minuto.

### Requisitos:
- Servidor Linux con CUDA drivers instalados
- Python 3.10+
- `ffmpeg`

### Instalación:
```bash
pip install fastapi uvicorn real-esrgan ffmpeg-python
```

### Código Base FastAPI (app.py)
```python
from fastapi import FastAPI, BackgroundTasks
import uuid

app = FastAPI()

@app.post("/upscale/submit")
async def submit_job(payload: dict):
    # Lógica para descargar video y aplicar Real-ESRGAN
    job_id = str(uuid.uuid4())
    return {"job_id": job_id}

@app.get("/upscale/status/{job_id}")
async def get_status(job_id: str):
    return {"job_id": job_id, "status": "PROCESSING", "progress": 0.5, "estimated_seconds": 120, "estimated_cost": 0, "result_url": None, "error": None}

@app.get("/upscale/download/{job_id}")
async def download_result(job_id: str):
    # Retornar el archivo procesado
    pass
```

### Configuración en MediaVault:
Ve a `Ajustes -> Escalado IA`, selecciona `Personalizado` e ingresa la URL base de tu servidor (ej. `http://tu-ip:8000`).
