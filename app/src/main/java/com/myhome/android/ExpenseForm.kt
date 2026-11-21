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

/**
 * 지출 추가·수정 입력 화면이다.
 * initial에 ID가 있으면 수정, 없으면 등록으로 처리한다.
 * 저장 성공 시 저장한 지출일을 전달해 해당 월 목록을 다시 조회한다.
 */
@Composable
fun ExpenseForm(
    initial: Bundle,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
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
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

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

        // 수정 가능 여부는 서버가 실제 고정 지출 납부 연결을 기준으로 판단한다.
        if (id != null) {
            Text(
                text = "고정 지출 납부로 생성된 항목은 여기서 수정할 수 없습니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }

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
                Text(if (saving) "저장 중…" else "저장")
            }

            TextButton(
                enabled = !saving,
                onClick = onBack
            ) {
                Text("취소")
            }
        }
    }
}