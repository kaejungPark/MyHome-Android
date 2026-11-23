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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 지출 추가·수정·삭제 화면이다.
 * 저장 성공 시 지출일을 전달하고, 삭제 성공 시 목록 재조회를 요청한다.
 */
@Composable
fun ExpenseForm(
    initial: Bundle,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 날짜 선택 창을 띄울 때 사용할 현재 화면의 Context를 가져온다.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ID가 있는 입력값만 기존 지출 수정으로 처리한다.
    val id = if (initial.containsKey("id")) {
        initial.getLong("id")
    } else {
        null
    }

    // 등록 시 필수 지출일은 기기의 오늘 날짜로 시작한다.
    val today = remember {
        val now = Calendar.getInstance()
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH)
        )
    }

    // 입력값은 화면 회전 후에도 유지한다.
    var title by rememberSaveable {
        mutableStateOf(initial.getString("title").orEmpty())
    }
    var amountText by rememberSaveable {
        mutableStateOf(initial.getString("amount").orEmpty())
    }
    var expenseDate by rememberSaveable {
        mutableStateOf(initial.getString("expenseDate") ?: today)
    }
    var categoryId by rememberSaveable {
        mutableStateOf(
            if (initial.containsKey("categoryId")) {
                initial.getLong("categoryId")
            } else {
                null
            }
        )
    }
    var expenseType by rememberSaveable {
        mutableStateOf(initial.getString("expenseType") ?: "VARIABLE")
    }
    var paymentMethod by rememberSaveable {
        mutableStateOf(initial.getString("paymentMethod").orEmpty())
    }
    var memo by rememberSaveable {
        mutableStateOf(initial.getString("memo").orEmpty())
    }

    // 분류 조회와 저장 상태를 따로 관리한다.
    var categories by remember {
        mutableStateOf<List<ExpenseCategory>>(emptyList())
    }
    var categoryLoading by remember { mutableStateOf(true) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var categoryReload by remember { mutableStateOf(0) }
    var categoryExpanded by remember { mutableStateOf(false) }
    // 저장·삭제 요청 중에는 입력과 버튼, 뒤로 가기를 잠근다.
    var saving by remember { mutableStateOf(false) }
    // 입력 검증과 저장 실패 메시지다.
    var formError by remember { mutableStateOf<String?>(null) }
    // 삭제 확인창 표시 여부와 삭제 실패 메시지를 관리한다.
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // 최초 진입과 재시도 시 분류 목록을 조회한다.
    LaunchedEffect(categoryReload) {
        categoryLoading = true
        categoryError = null

        try {
            categories = CategoryApi.fetchCategories()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            categoryError = "분류 목록을 불러오지 못했습니다."
        } finally {
            categoryLoading = false
        }
    }

    // 저장 중에는 뒤로 가기로 입력 화면을 닫지 않는다.
    BackHandler {
        if (!saving) {
            onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (id == null) "지출 추가" else "지출 수정",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("지출 제목 *") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("금액 *") },
            suffix = { Text("원") },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 분류를 임의로 선택하지 않고 사용자가 선택하도록 한다.
        Box {
            OutlinedButton(
                enabled = !saving &&
                        !categoryLoading &&
                        categoryError == null &&
                        categories.isNotEmpty(),
                onClick = { categoryExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedName = categories
                    .firstOrNull { it.id == categoryId }
                    ?.name

                Text(
                    if (categoryLoading) {
                        "분류를 불러오는 중…"
                    } else {
                        selectedName ?: "분류 선택 *"
                    }
                )
            }

            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            categoryId = category.id
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        categoryError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)

            TextButton(
                enabled = !saving,
                onClick = { categoryReload++ }
            ) {
                Text("분류 다시 불러오기")
            }
        }

        if (!categoryLoading && categoryError == null && categories.isEmpty()) {
            Text("등록된 분류가 없습니다.")
        }

        // 달력을 열 때 현재 선택된 지출일을 기준으로 표시한다.
        OutlinedButton(
            enabled = !saving,
            onClick = {
                val parts = expenseDate.split("-")

                DatePickerDialog(
                    context,
                    { _, year, monthIndex, day ->
                        expenseDate = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            year,
                            monthIndex + 1,
                            day
                        )
                    },
                    parts[0].toInt(),
                    parts[1].toInt() - 1,
                    parts[2].toInt()
                ).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("지출일 *  $expenseDate")
        }

        Text("지출 유형 *")

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = expenseType == "VARIABLE",
                enabled = !saving,
                onClick = { expenseType = "VARIABLE" },
                label = { Text("변동 지출") }
            )

            FilterChip(
                selected = expenseType == "FIXED",
                enabled = !saving,
                onClick = { expenseType = "FIXED" },
                label = { Text("고정 지출") }
            )
        }

        OutlinedTextField(
            value = paymentMethod,
            onValueChange = { paymentMethod = it },
            label = { Text("결제 수단 (선택)") },
            placeholder = { Text("예: 카드, 현금") },
            singleLine = true,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모 (선택)") },
            minLines = 2,
            maxLines = 4,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )

        formError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !saving &&
                        !categoryLoading &&
                        categoryError == null &&
                        categories.isNotEmpty(),
                onClick = {
                    // 서버의 금액 제한에 맞춰 정수 16자리, 소수 2자리까지 허용한다.
                    val rawAmount = amountText.trim()
                    val amount = rawAmount.toBigDecimalOrNull()
                    val selectedCategoryId = categoryId

                    // 필수값과 서버의 문자열 길이 제한을 먼저 검사한다.
                    val validationMessage = when {
                        title.isBlank() ->
                            "지출 제목을 입력해 주세요."
                        title.trim().length > 200 ->
                            "제목은 200자 이내로 입력해 주세요."
                        !rawAmount.matches(
                            Regex("[0-9]{1,16}(\\.[0-9]{1,2})?")
                        ) || amount == null || amount.signum() <= 0 ->
                            "금액은 0보다 큰 숫자로 입력해 주세요. 소수는 2자리까지 가능해요."
                        selectedCategoryId == null ||
                                categories.none { it.id == selectedCategoryId } ->
                            "분류를 선택해 주세요."
                        expenseDate.take(4).toInt() !in 1..9998 ->
                            "지출일의 연도는 1~9998년으로 선택해 주세요."
                        paymentMethod.trim().length > 50 ->
                            "결제 수단은 50자 이내로 입력해 주세요."
                        memo.trim().length > 2000 ->
                            "메모는 2000자 이내로 입력해 주세요."
                        else -> null
                    }

                    formError = validationMessage

                    if (validationMessage == null &&
                        amount != null &&
                        selectedCategoryId != null
                    ) {
                        // 요청 시작 전에 중복 저장을 차단한다.
                        saving = true

                        scope.launch {
                            try {
                                ExpenseApi.saveExpense(
                                    id = id,
                                    categoryId = selectedCategoryId,
                                    amount = amount,
                                    expenseDate = expenseDate,
                                    expenseType = expenseType,
                                    paymentMethod = paymentMethod,
                                    title = title,
                                    memo = memo
                                )

                                // 저장한 날짜의 월을 목록 화면에서 표시하도록 전달한다.
                                onSaved(expenseDate)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: ApiHttpException) {
                                formError = when (e.statusCode) {
                                    409 ->
                                        "고정 지출 납부로 생성된 항목입니다. 고정 지출 화면에서 납부 기록을 관리해 주세요."
                                    404 ->
                                        "지출이 삭제되었거나 찾을 수 없습니다. 목록을 다시 확인해 주세요."
                                    else -> e.message
                                        ?: "저장하지 못했습니다."
                                }
                            } catch (e: Exception) {
                                formError = e.localizedMessage
                                    ?: "저장하지 못했습니다."
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "처리 중…" else "저장")
            }

            TextButton(
                enabled = !saving,
                onClick = onBack
            ) {
                Text("취소")
            }

            // 취소 버튼 바로 오른쪽에 작은 삭제 버튼을 배치한다.
            if (id != null) {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        deleteError = null
                        showDeleteDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            }
        }
    }

    // 기존 지출의 삭제 버튼을 눌렀을 때 확인창을 표시한다.
    if (showDeleteDialog && id != null) {
        AlertDialog(
            onDismissRequest = {
                // 요청 중에는 창 밖 터치나 뒤로 가기로 닫지 않는다.
                if (!saving) {
                    showDeleteDialog = false
                }
            },
            title = {
                Text("지출을 삭제할까요?")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 아직 저장하지 않은 수정 제목 대신 원래 항목명을 표시한다.
                    Text(
                        text = initial.getString("title").orEmpty()
                    )

                    Text("삭제한 지출은 복구할 수 없습니다.")

                    // 서버가 삭제를 거절하거나 연결이 실패하면 창 안에 안내한다.
                    deleteError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        // 빠르게 여러 번 눌러도 요청을 중복 실행하지 않는다.
                        if (!saving) {
                            saving = true
                            deleteError = null

                            scope.launch {
                                try {
                                    ExpenseApi.deleteExpense(id)

                                    // 서버 삭제 성공 후 확인창을 닫고 목록을 갱신한다.
                                    showDeleteDialog = false
                                    onDeleted()
                                } catch (e: CancellationException) {
                                    // 화면 종료로 취소된 작업은 취소 상태를 유지한다.
                                    throw e
                                } catch (e: ApiHttpException) {
                                    when (e.statusCode) {
                                        404 -> {
                                            // 다른 화면에서 이미 삭제한 경우에도 목록을 갱신한다.
                                            showDeleteDialog = false
                                            onDeleted()
                                        }

                                        409 -> {
                                            // 고정 지출 납부에 연결된 항목은 서버에서 삭제를 제한한다.
                                            deleteError =
                                                "고정 지출 납부로 생성된 항목입니다. " +
                                                        "고정 지출 화면에서 납부를 취소해 주세요."
                                        }

                                        else -> {
                                            deleteError = e.message
                                                ?: "삭제하지 못했습니다."
                                        }
                                    }
                                } catch (e: Exception) {
                                    // 연결 실패 시 확인창을 유지해 다시 시도할 수 있게 한다.
                                    deleteError =
                                        "삭제 결과를 확인하지 못했습니다. " +
                                                "연결 상태를 확인한 뒤 다시 시도해 주세요."
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (saving) "삭제 중…" else "삭제")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        // 삭제 요청 없이 확인창만 닫는다.
                        showDeleteDialog = false
                    }
                ) {
                    Text("취소")
                }
            }
        )
    }
}