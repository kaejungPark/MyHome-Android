package com.myhome.android

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.TimeZone

/**
 * 물품 추가와 수정에서 함께 사용하는 화면이다.
 * initial은 목록에서 전달한 초기값 묶음이다. 추가일 때는 비어 있고, 수정일 때는 ID와 기존 값이 들어 있다.
 * onSaved는 저장 성공 후 목록으로 돌아가 최신 데이터를 조회하는 함수다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingForm(
    initial: Bundle,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {

    // Bundle.getLong()은 키가 없어도 0을 반환하므로, 먼저 ID가 있는지 확인한다.
    val id = if (initial.containsKey("id"))initial.getLong("id") else null

    // 추가 시 기본 수량은 1이다. 수정 시 기존 값으로 시작하고 화면을 회전해도 입력값을 유지한다.
    var name by rememberSaveable { mutableStateOf(initial.getString("name").orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(initial.getString("quantity") ?: "1") }
    var unit by rememberSaveable { mutableStateOf(initial.getString("unit").orEmpty()) }

    // 서버의 날짜 문자열과 달력이 사용하는 UTC 기준 날짜를 서로 변환한다.
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // 수정이면 기존 예정일을 선택하고, 추가이거나 예정일이 없으면 선택을 비워 둔다.
    val initialDate = initial.getString("dueDate")
    val initialMillis = remember(initialDate) {
        initialDate?.takeIf { it.isNotBlank() }?.let { dateFormat.parse(it)?.time }
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    // 저장 코드에서 사용하던 dueDate를 달력의 현재 선택값으로 만든다.
    val dueDate = datePickerState.selectedDateMillis?.let { millis ->
        dateFormat.format(java.util.Date(millis))
    }.orEmpty()

    var memo by rememberSaveable { mutableStateOf(initial.getString("memo").orEmpty()) }

    // 저장 중에는 버튼을 잠그고, 실패하면 작성한 내용 아래에 오류를 표시한다.
    var saving by remember { mutableStateOf(false) }
    // 삭제 버튼을 누르면 true가 되어 확인창이 열린다.
    var showDeleteDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 입력 화면에서 휴대폰의 뒤로 가기를 누르면 장보기 목록으로 돌아간다.
    BackHandler {
        if (!saving) onBack()
    }

    if (showDeleteDialog && id != null) {
        // 입력란에서 이름을 바꿨어도 삭제 대상은 서버에 저장된 원래 물품이다.
        var originalName = initial.getString("name").orEmpty()

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("물품 삭제") },
            text = { Text("‘${originalName}’ 항목을 장보기 목록에서 삭제할까요?") },

            // 취소하면 확인창만 닫고 작성하던 내용을 유지한다.
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            },

            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    if (!saving) {
                        // 요청 전에 확인창을 닫고 저장·삭제 버튼의 중복 클릭을 막는다.
                        saving = true
                        showDeleteDialog = false
                        error = ""

                        scope.launch {
                            try {
                                ShoppingApi.deleteShoppingItem(id)
                                // 기존 콜백이 현재 탭으로 돌아가 목록을 다시 조회한다.
                                onSave()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // 삭제 실패 시 수정 화면에 남아 결과를 안내한다.
                                error = e.localizedMessage ?: "물품을 삭제하지 못했습니다."
                            } finally {
                                saving = false
                            }
                        }
                    }
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    Column(
        modifier =  modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (id == null) "물품 추가" else "물품 수정",
            style = MaterialTheme.typography.headlineMedium
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("물품명 *") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        // 수량과 단위는 한 줄에 배치한다. 예를 들어 수량은 1.5, 단위는 kg이다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("수량 *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("단위 * (개, 팩)") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.weight(1f)
            )
        }

        // 선택 여부를 달력 위에 표시한다. 날짜는 필수가 아니므로 다시 지울 수 있다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = if (dueDate.isEmpty()) "구매 예정일 (선택)" else "구매 예정일 · $dueDate",
                style = MaterialTheme.typography.titleSmall
            )

            if (dueDate.isNotEmpty()) {
                TextButton(
                    enabled = !saving,
                    onClick = { datePickerState.selectedDateMillis = null }
                ) {
                    Text("날짜 지우기")
                }
            }
        }

        // 화면을 열자마자 월간 달력을 표시한다.
        // 제목과 큰 날짜 헤더를 생략해 달력이 차지하는 세로 공간을 줄인다.
        DatePicker(
            state = datePickerState,
            modifier = Modifier.fillMaxWidth(),
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                // 오늘 날짜의 테두리를 숨기고 숫자 색도 일반 날짜와 같게 표시한다.
                todayDateBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                todayContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모 (선택)") },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !saving,
                onClick = {
                    // 백엔드가 허용하는 길이와 수량 형식에 맞는지 먼저 확인한다.
                    val amount = quantity.trim()
                    error = when {
                        name.trim().length !in 1..100 ->
                            "물품명은 1~100자로 입력해 주세요."

                        !Regex("""\d{1,8}(\.\d{1,2})?""").matches(amount) ||
                                amount.toBigDecimalOrNull()?.signum() != 1 ->
                            "수량은 0보다 크게, 정수 8자리·소수 2자리 이내로 입력해 주세요."

                        unit.trim().length !in 1..20 ->
                            "단위는 1~20자로 입력해 주세요."

                        memo.trim().length > 500 ->
                            "메모는 500자까지 입력할 수 있어요."

                        else -> ""
                    }

                    if (error.isEmpty() && !saving) {
                        saving = true
                        scope.launch {
                            try {
                                // 분리한 장보기 API를 호출한다. id가 null이면 등록, 있으면 수정이다.
                                ShoppingApi.saveShoppingItem(
                                    id = id,
                                    name = name,
                                    quantity = amount,
                                    unit = unit,
                                    purchaseDueDate = dueDate,
                                    memo = memo
                                )
                                onSave()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // 실패하면 입력값을 유지해 같은 화면에서 다시 저장할 수 있게 한다.
                                error = e.localizedMessage ?: "저장에 실패했습니다."
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "저장 중…" else "저장")
            }

            TextButton(enabled = !saving, onClick = onBack) {
                Text("취소")
            }

            // 기존 물품의 수정 화면에서만 삭제 버튼을 표시한다.
            if (id != null) {
                TextButton(
                    enabled = !saving,
                    onClick = { showDeleteDialog = true }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}