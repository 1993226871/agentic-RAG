import os
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel, Field
from sentence_transformers import CrossEncoder


class RerankRequest(BaseModel):
    query: str = Field(..., min_length=1)
    documents: List[str] = Field(default_factory=list)
    top_k: int = Field(3, ge=1)


class RerankRow(BaseModel):
    index: int
    score: float


class RerankResponse(BaseModel):
    results: List[RerankRow]


MODEL_NAME = os.getenv("BGE_RERANK_MODEL", "BAAI/bge-reranker-base")
DEVICE = os.getenv("BGE_RERANK_DEVICE", "cpu")

app = FastAPI(title="BGE Reranker Service", version="1.0.0")
model = CrossEncoder(MODEL_NAME, device=DEVICE)


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "device": DEVICE}


@app.post("/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest):
    if not req.documents:
        return RerankResponse(results=[])

    pairs = [[req.query, doc] for doc in req.documents]
    scores = model.predict(pairs).tolist()
    ranked = sorted(
        [{"index": i, "score": float(score)} for i, score in enumerate(scores)],
        key=lambda x: x["score"],
        reverse=True,
    )
    return RerankResponse(results=ranked[: req.top_k])
