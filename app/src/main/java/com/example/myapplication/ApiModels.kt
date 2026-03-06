package com.example.myapplication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthRequest(
    val username: String,
    val password: String,
    val key: String
)

@Serializable
data class TokenResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String
)

@Serializable
data class DoctorMeResponse(
    val username: String,
    @SerialName("full_name")
    val fullName: String? = null,
    val specialization: String? = null,
    val role: String
)

@Serializable
data class PatientMeResponse(
    val id: Int,
    val username: String? = null,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("date_of_birth")
    val dateOfBirth: String,
    @SerialName("contact_info")
    val contactInfo: String? = null,
    @SerialName("doctor_id")
    val doctorId: Int,
    val role: String
)

@Serializable
data class PatientDisplayResponse(
    val id: Int,
    @SerialName("doctor_id")
    val doctorId: Int,
    @SerialName("full_name")
    val fullName: String,
    @SerialName("date_of_birth")
    val dateOfBirth: String,
    @SerialName("contact_info")
    val contactInfo: String? = null,
    @SerialName("created_at")
    val createdAt: String
)

@Serializable
data class GlucoseDataResponse(
    val labels: List<String>,
    val data: List<Double>
)

@Serializable
data class ChartPoint(
    val x: String,
    val y: Double
)

@Serializable
data class ComprehensiveDataResponse(
    val glucose: List<ChartPoint>,
    val insulin: List<ChartPoint>,
    val carbs: List<ChartPoint>
)

@Serializable
data class RecommendationsResponse(
    val recommendations: List<String>
)

@Serializable
data class PatientParametersResponse(
    val parameters: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class SimulatorScenarioResponse(
    @SerialName("scenario_id")
    val scenarioId: Int,
    @SerialName("patient_id")
    val patientId: Int,
    @SerialName("scenario_data")
    val scenarioData: Map<String, JsonElement>
)

@Serializable
data class TimeseriesDataPointRequest(
    val timestamp: String,
    @SerialName("record_type")
    val recordType: String,
    val value: Double? = null,
    val details: String? = null
)

@Serializable
data class TimeseriesDataRequest(
    @SerialName("data_points")
    val dataPoints: String
)
