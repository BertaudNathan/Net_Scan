package com.example.MobileM2.Net_Scan.data.signal

import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Source scriptée pour les tests automatiques et les démonstrations sans SIM : rejoue les
 * lectures en boucle. L'écran l'indique toujours par un badge « Données simulées ».
 */
class SimulatedSignalSource(
    private val samples: List<SignalSample> = DEFAULT_SAMPLES,
    private val intervalMillis: Long = 1_000,
    private val operator: OperatorStatus = OperatorStatus.Connected("Simulation", NetworkTechnology.LTE),
    private val availability: SignalAvailability = SignalAvailability.AVAILABLE,
) : SignalSource {
    init {
        require(samples.isNotEmpty()) { "At least one sample is required" }
    }

    override val isSimulated = true

    override fun observeOperator(): Flow<OperatorStatus> = flowOf(operator)

    override fun availability() = availability

    override fun observeSignal(): Flow<SignalSample> = flow {
        while (true) {
            for (sample in samples) {
                emit(sample)
                delay(intervalMillis)
            }
        }
    }

    companion object {
        val DEFAULT_SAMPLES = listOf(-92, -95, -88, -97, -91).map { rsrp ->
            SignalSample(
                inService = true,
                technology = NetworkTechnology.LTE,
                rsrpDbm = rsrp,
                sinrDb = 7,
                band = 20,
                cellId = 123_456,
            )
        }
    }
}
