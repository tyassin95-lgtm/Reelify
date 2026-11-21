package app.reelify.content

import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TreatmentKnowledgeCatalog
import app.reelify.core.content.VerificationStatus
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.template.BuiltInTemplates
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class TreatmentKnowledgeAndTopicCatalogTest {

    private val sevenTreatments = listOf(
        "aerolase",
        "picostar",
        "cfu",
        "dermablate",
        "lasemd",
        "diamond_peeling",
        "diamond_glow",
    )

    @Test fun `every built-in treatment and template combination has at least 10 distinct topics`() {
        val templates = BuiltInTemplates.all.map { it.id }
        assertThat(templates).hasSize(6)

        var totalVerified = 0
        for (tid in sevenTreatments) {
            val treatment = TreatmentSeed.treatments.first { it.id == tid }
            for (tplId in templates) {
                val list = TopicLibrary.topicsFor(treatment, tplId)
                assertWithMessage("$tid x $tplId topic count").that(list.size).isAtLeast(10)
                assertThat(list.map { it.key }.toSet()).hasSize(list.size)
                assertThat(list.all { it.title.en.isNotBlank() && it.title.ar.isNotBlank() }).isTrue()
                assertThat(list.all { it.hookAngle.en.isNotBlank() && it.hookAngle.ar.isNotBlank() }).isTrue()
                totalVerified += list.size
            }
        }
        assertThat(totalVerified).isAtLeast(420)

        // Unconfirmed Cleopatra also has 10 protocol-safe non-device-assuming topics per template
        val cleopatra = TreatmentSeed.treatments.first { it.id == "cleopatra" }
        assertThat(cleopatra.identityConfirmed).isFalse()
        templates.forEach { tplId ->
            val pendingList = TopicLibrary.topicsFor(cleopatra, tplId)
            assertWithMessage("cleopatra pending x $tplId").that(pendingList.size).isAtLeast(10)
            assertThat(pendingList.all { it.factIds.isEmpty() }).isTrue()
        }
    }

    @Test fun `all topic fact references resolve cleanly to verified facts`() {
        TopicLibrary.treatmentTopics.forEach { topic ->
            if (topic.factIds.isNotEmpty()) {
                val pack = TreatmentKnowledgeCatalog.byId[topic.treatmentId]
                assertWithMessage("pack for ${topic.key}").that(pack).isNotNull()
                topic.factIds.forEach { fid ->
                    assertWithMessage("fact $fid on ${topic.key}").that(pack!!.hasReference(fid)).isTrue()
                }
            }
        }
    }

    @Test fun `cfu elife is documented strictly as HIFU focused ultrasound and never a laser`() {
        val pack = TreatmentKnowledgeCatalog.cfu
        assertThat(pack.manufacturer).isEqualTo("Biotec Italia")
        assertThat(pack.model).contains("CFU")
        assertThat(pack.technologyEn).contains("Ultrasound")
        assertThat(pack.technologyAr).contains("مو ليزر")
        assertThat(pack.allFacts().any { it.id == "cfu_mech_hifu" }).isTrue()

        val cfuTreatment = TreatmentSeed.treatments.first { it.id == "cfu" }
        assertThat(cfuTreatment.needsConfiguration).isFalse()
        assertThat(cfuTreatment.forbiddenClaims.joinToString(" ").lowercase()).contains("laser")
    }

    @Test fun `picostar pack contains wavelengths pulse width photoacoustic mechanism and handpieces`() {
        val pack = TreatmentKnowledgeCatalog.picostar
        val allText = (listOf(pack.technologyEn) + pack.allFacts().map { it.statementEn }).joinToString(" ")
        assertThat(allText).contains("1064")
        assertThat(allText).contains("532")
        assertThat(allText.lowercase()).contains("photoacoustic")
        assertThat(allText).contains("Zoom")
        assertThat(allText).contains("Flat-Top")
        assertThat(allText).contains("MicroSpot")
        val pico = TreatmentSeed.treatments.first { it.id == "picostar" }
        assertThat(pico.needsConfiguration).isFalse()
    }

    @Test fun `cleopatra ambiguity is preserved until explicitly confirmed as DiamondGlow`() {
        val cleopatra = TreatmentSeed.treatments.first { it.id == "cleopatra" }
        assertThat(cleopatra.needsConfiguration).isTrue()
        assertThat(cleopatra.identityConfirmed).isFalse()
        assertThat(TreatmentKnowledgeCatalog.packFor("cleopatra", null, true)!!.verificationStatus)
            .isEqualTo(VerificationStatus.CLINIC_PROTOCOL_REQUIRED)

        val dg = TreatmentSeed.treatments.first { it.id == "diamond_glow" }
        assertThat(dg.needsConfiguration).isFalse()
        assertThat(dg.identityConfirmed).isTrue()
        val dgPack = TreatmentKnowledgeCatalog.packFor("diamond_glow", null, false)!!
        val dgText = (listOf(dgPack.technologyEn) + dgPack.allFacts().map { it.statementEn }).joinToString(" ")
        assertThat(dgText).contains("3-in-1")
        assertThat(dgText).contains("Pro-Infusion")
        assertThat(dgText.lowercase()).contains("lymphatic")
    }
}
