package app.reelify.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.content.ExampleStatus
import app.reelify.core.content.StyleExample
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.Eyebrow
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PillAction
import app.reelify.core.designsystem.PrimaryButton
import app.reelify.core.designsystem.ReelTemplateCard
import app.reelify.core.designsystem.ReelTextField
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.ScreenFrame
import app.reelify.core.designsystem.ScriptText
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.ColorLook
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.CameraFacing
import app.reelify.core.template.LocalizedText
import app.reelify.core.template.ReelTemplate
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.text
import app.reelify.feature.common.tr
import app.reelify.core.util.Ids
import kotlinx.coroutines.launch

/**
 * Owner review of the Iraqi Arabic reference lines that steer the scriptwriter. Approve the ones
 * that sound like you, correct the rest, reject anything unnatural. Nothing here is presented as
 * native-speaker reviewed until YOU approve it.
 */
@Composable
fun IraqiStyleScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val examples by c.content.observeIraqiExamples().collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<StyleExample?>(null) }
    val reviewed = examples.count { it.status == ExampleStatus.APPROVED || it.status == ExampleStatus.CORRECTED }
    ScreenFrame(tr("Iraqi style guide", "دليل اللهجة العراقية"), onBack, subtitle = tr("$reviewed of ${examples.size} reviewed by you", "راجعت $reviewed من ${examples.size}")) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                InfoBanner(tr(
                    "These lines teach the AI your spoken register. They were written for Reelify and have NOT been checked by a native Iraqi reviewer — approve the ones that sound right, correct the rest. Approved lines are prioritised in every Iraqi script.",
                    "هاي الجمل تعلّم الذكاء الاصطناعي أسلوبك. انكتبت لريليفاي وما راجعها شخص عراقي — وافق على اللي تحسها صح، وصحّح الباقي. الجمل الموافق عليها تنطي أولوية بكل سكربت عراقي.",
                ), color = Rf.Info)
            }
            item {
                PillAction(tr("Add your own line", "ضيف جملة من عندك"), Icons.Rounded.Edit, { editing = StyleExample("own-" + Ids.new(), "own", "", status = ExampleStatus.APPROVED, builtIn = false) })
            }
            items(examples, key = { it.id }) { e ->
                CinematicCard {
                    Row {
                        Eyebrow(e.category.replace('_', ' '), Modifier.weight(1f))
                        StatusPill(e.status.label, when (e.status) { ExampleStatus.APPROVED, ExampleStatus.CORRECTED -> Rf.Success; ExampleStatus.REJECTED -> Rf.Danger; else -> Rf.TextTertiary })
                    }
                    Spacer(Modifier.height(8.dp))
                    ScriptText(e.better, style = MaterialTheme.typography.bodyLarge)
                    if (e.formal.isNotBlank()) { Spacer(Modifier.height(4.dp)); ScriptText("✗ " + e.formal, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillAction(tr("Approve", "موافق"), Icons.Rounded.Check, { scope.launch { c.content.saveExample(e.copy(status = ExampleStatus.APPROVED)) } }, enabled = e.status != ExampleStatus.APPROVED)
                        PillAction(tr("Correct", "صحّح"), Icons.Rounded.Edit, { editing = e })
                        PillAction(tr("Reject", "ارفض"), Icons.Rounded.Close, { scope.launch { c.content.saveExample(e.copy(status = ExampleStatus.REJECTED)) } }, enabled = e.status != ExampleStatus.REJECTED)
                        if (e.status != ExampleStatus.UNREVIEWED && e.builtIn) PillAction(tr("Reset", "إرجاع"), Icons.Rounded.Refresh, { scope.launch { c.content.resetExample(e.id) } })
                    }
                }
            }
        }
    }
    editing?.let { e ->
        var text by remember(e.id) { mutableStateOf(e.better) }
        var category by remember(e.id) { mutableStateOf(e.category) }
        ModalBottomSheet({ editing = null }, containerColor = Rf.Surface) {
            Column(Modifier.padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("How would you say it?", "شلون تكولها انت؟"), style = MaterialTheme.typography.headlineSmall)
                ReelTextField(text, { text = it }, minLines = 3, label = tr("Natural Iraqi line", "الجملة بالعراقي"))
                if (!e.builtIn) ReelTextField(category, { category = it }, singleLine = true, label = tr("Category (hook, cta, explanation…)", "النوع (افتتاحية، دعوة، شرح…)"))
                PrimaryButton(tr("Save", "حفظ"), {
                    scope.launch { c.content.saveExample(e.copy(better = text.trim(), category = category.trim().ifBlank { "own" }, status = if (e.builtIn && text.trim() != e.better) ExampleStatus.CORRECTED else ExampleStatus.APPROVED)) }
                    editing = null
                }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
            }
        }
    }
}

/**
 * Advanced: customise a template and save it as your own (built-ins never change). The shot
 * structure stays three equal shots; you can change look, captions, music, transitions,
 * branding, beauty default and per-shot camera/instruction.
 */
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val templates by c.templates.observeAll().collectAsStateWithLifecycle(initialValue = BuiltInTemplates.all)
    var editing by remember { mutableStateOf<ReelTemplate?>(null) }
    ScreenFrame(tr("Templates", "القوالب"), onBack, subtitle = tr("Built-in formats stay fixed; customise a copy", "القوالب الأساسية ثابتة؛ عدّل نسخة")) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(templates, key = { it.id + it.version }) { t ->
                Column {
                    ReelTemplateCard(t, t.name.text(), t.description.text(), t.shots.sortedBy { it.index }.map { it.role.label.text() }, false, { editing = t },
                        badge = if (t.builtIn) "v${t.version}" else tr("Custom v${t.version}", "مخصص v${t.version}"))
                    if (!t.builtIn) PillAction(tr("Delete", "حذف"), Icons.Rounded.Close, { scope.launch { c.templates.deleteCustom(t.id) } }, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
    editing?.let { t0 ->
        var t by remember(t0.id) { mutableStateOf(t0) }
        var name by remember(t0.id) { mutableStateOf(if (t0.builtIn) t0.name.en + " (mine)" else t0.name.en) }
        ModalBottomSheet({ editing = null }, containerColor = Rf.Surface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Customise template", "تعديل القالب"), style = MaterialTheme.typography.headlineSmall)
                ReelTextField(name, { name = it }, singleLine = true, label = tr("Name", "الاسم"))
                Eyebrow(tr("Captions", "الترجمة"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CaptionPresetId.PREMIUM_MINIMAL, CaptionPresetId.BOLD_ENGAGEMENT, CaptionPresetId.CLEAN_CLINICAL).forEach { id -> Chip(id.label, t.captionPresetId == id, { t = t.copy(captionPresetId = id) }) }
                }
                Eyebrow(tr("Music", "الموسيقى"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuiltInTemplates.musicPresets.keys.forEach { id -> Chip(id.replace('_', ' '), t.musicPresetId == id, { t = t.copy(musicPresetId = id) }) }
                }
                Eyebrow(tr("Transitions", "الانتقالات"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuiltInTemplates.transitionPresets.keys.forEach { id -> Chip(id.replace('_', ' '), t.transitionPresetId == id, { t = t.copy(transitionPresetId = id) }) }
                }
                Eyebrow(tr("Colour look", "الألوان"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorLook.entries.forEach { l -> Chip(l.label, t.colorLook == l, { t = t.copy(colorLook = l) }) }
                }
                Eyebrow(tr("Beauty for talking shots", "التجميل للقطات الكلام"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeautyPreset.entries.filter { it != BeautyPreset.CUSTOM }.forEach { p -> Chip(p.label, t.talkingBeauty == p, { t = t.copy(talkingBeauty = p) }) }
                }
                Eyebrow(tr("Branding", "الهوية"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuiltInTemplates.brandingPresets.keys.forEach { id -> Chip(id, t.brandingPresetId == id, { t = t.copy(brandingPresetId = id) }) }
                }
                t.shots.sortedBy { it.index }.forEach { shot ->
                    Eyebrow(tr("Shot ${shot.index} · ", "اللقطة ${shot.index} · ") + shot.role.label.text())
                    ReelTextField(shot.instruction.en, { v -> t = t.copy(shots = t.shots.map { if (it.index == shot.index) it.copy(instruction = LocalizedText(v, if (v == shot.instruction.en) shot.instruction.ar else v)) else it }) }, label = tr("Filming instruction", "تعليمات التصوير"))
                    val cam = BuiltInTemplates.cameraPresets[shot.cameraPresetId]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BuiltInTemplates.cameraPresets.values.filter { (it.facing == CameraFacing.FRONT) == (cam?.facing == CameraFacing.FRONT) || true }.forEach { p ->
                            Chip(p.id.replace('_', ' '), shot.cameraPresetId == p.id, { t = t.copy(shots = t.shots.map { if (it.index == shot.index) it.copy(cameraPresetId = p.id) else it }) })
                        }
                    }
                }
                Text(tr("Saved as your own template (new version). Existing reels keep the version they were made with.", "ينحفظ كقالب خاص بيك (نسخة جديدة). الريلز القديمة تبقى على نسختها."), style = MaterialTheme.typography.bodySmall)
                PrimaryButton(tr("Save template", "احفظ القالب"), {
                    scope.launch { c.templates.saveCustom(t.copy(name = LocalizedText(name.trim().ifBlank { t.name.en }, name.trim().ifBlank { t.name.ar }), previewAsset = t.previewAsset)) }
                    editing = null
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
