package com.oreki.stumpd.data.models

import com.oreki.stumpd.domain.model.DeliverySnapshot

/**
 * Aligns persisted undo stack length with the number of restored ball-by-ball deliveries.
 * Matches [com.oreki.stumpd.viewmodel.ScoringViewModel.resumeMatch] behaviour.
 */
fun deliverySnapshotsAlignedToDeliveryCount(
    restored: List<DeliverySnapshot>,
    allDeliveriesCount: Int,
): List<DeliverySnapshot> = when {
    restored.size == allDeliveriesCount -> restored
    restored.size > allDeliveriesCount && allDeliveriesCount > 0 ->
        restored.take(allDeliveriesCount)
    else -> emptyList()
}
