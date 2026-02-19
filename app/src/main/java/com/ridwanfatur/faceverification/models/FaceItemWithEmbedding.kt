package com.ridwanfatur.faceverification.models

import com.google.mediapipe.tasks.components.containers.Embedding

data class FaceItemWithEmbedding(
    val faceItem: FaceItem,
    val embeddings: List<Embedding>
)