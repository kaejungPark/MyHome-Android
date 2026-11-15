package com.myhome.android

import android.R
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

/**
 * 물품 추가와 수정에서 함께 사용하는 화면이다.
 * initial은 목록에서 전달한 초기값 묶음이다. 추가일 때는 비어 있고, 수정일 때는 ID와 기존 값이 들어 있다.
 * onSaved는 저장 성공 후 목록으로 돌아가 최신 데이터를 조회하는 함수다.
 */
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
    var dueDate by rememberSaveable { mutableStateOf(initial.getString("dueDate").orEmpty()) }
    var memo by rememberSaveable { mutableStateOf(initial.getString("memo").orEmpty()) }

    // 저장 중에는 버튼을 잠그고, 실패하면 작성한 내용 아래에 오류를 표시한다.
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 입력 화면에서 휴대폰의 뒤로 가기를 누르면 장보기 목록으로 돌아간다.
    BackHandler {
        if (!saving) onBack()
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

        // 날짜는 선택 사항이다. 기존 예정일이 있으면 그 날짜에서 달력이 열린다.
        Row {
            TextButton(
                enabled = !saving,
                onClick = {
                    val calendar = Calendar.getInstance()
                    if (dueDate.isNotEmpty()) {
                        val (year, month, day) = dueDate.split("-").map { it.toInt() }
                        calendar.set(year, month - 1, day)
                    }

                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            // Android 달력의 월은 0부터 시작한다. 서버에는 yyyy-MM-dd로 전달한다.
                            dueDate = String.format(
                                Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day
                            )
                        },
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            ) {
                Text(dueDate.ifEmpty { "구매 예정일 선택" })
            }
            if (dueDate.isNotEmpty()) {
                TextButton(enabled = !saving, onClick = { dueDate = "" }) {
                    Text("날짜 지우기")
                }
            }
        }
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
        }
    }
}