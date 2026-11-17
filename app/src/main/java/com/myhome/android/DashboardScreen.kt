package com.myhome.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhome.android.ui.theme.MyHomeTheme
import kotlinx.coroutines.CancellationException
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home

// 대시보드에서 사용할 배경색과 강조색이다.
private val DashboardBackground = Color(0xFFF3F5FA)
private val DashboardNavy = Color(0xFF172B55)
private val DashboardLightBlue = Color(0xFFE5EDFF)
private val DashboardSecondary = Color(0xFF647087)

/**
 * 로그인 전에는 로그인 폼을 표시한다.
 * 로그인 후에는 생활비, 장보기, 일정 요약 영역을 표시한다.
 *
 * 장보기 카드에서 false를 전달하면 살 물품 탭,
 * true를 전달하면 구매 완료 탭으로 이동한다.
 */
@Composable
fun DashboardScreen(
    isLoggedIn: Boolean,
    onLoginSuccess: () -> Unit,
    onOpenShopping: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // 본문과 하단 메뉴를 세로로 배치한다.
    // MainActivity가 전달한 시스템 영역 여백을 적용한다.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DashboardBackground)
    ) {
        // 본문만 스크롤하고 하단 메뉴는 화면 아래에 고정한다.
        // 작은 화면이나 큰 글꼴 설정에서도 내용을 확인할 수 있다.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 왼쪽에는 앱 이름, 오른쪽에는 로그인 상태에 맞는 메뉴를 표시한다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 집 아이콘과 앱 이름을 가로로 배치한다.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 옆의 MyHome 글자로 앱 이름을 알 수 있어 별도 음성 설명은 생략한다.
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = DashboardNavy,
                        modifier = Modifier.size(30.dp)
                    )

                    Text(
                        text = "MyHome",
                        color = DashboardNavy,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isLoggedIn) {
                    // 로그아웃 기능은 다음 작업에서 연결한다.
                    // 현재는 비활성 버튼으로 표시해 동작 준비 중임을 나타낸다.
                    TextButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "로그아웃",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    // 로그인 전에는 사용자 아이콘과 로그인 안내를 표시한다.
                    // 실제 로그인은 아래에 있는 기존 로그인 폼에서 진행한다.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = DashboardSecondary,
                            modifier = Modifier.size(22.dp)
                        )

                        Text(
                            text = "로그인",
                            color = DashboardSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            // 화면의 대표 제목과 안내 문구를 묶어 배치한다.
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "우리 집 한눈에",
                    color = DashboardNavy,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "오늘의 생활을 확인해요",
                    color = DashboardSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (isLoggedIn) {
                DashboardSummary(
                    onOpenShopping = onOpenShopping
                )
            } else {
                // 흰색 카드 안에 기존 로그인 폼을 표시한다.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    contentColor = DashboardNavy
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "로그인",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        // 로그인 성공을 MainActivity에 전달한다.
                        LoginForm(
                            onLoginSuccess = onLoginSuccess
                        )
                    }
                }
            }
        }

        // 로그인 후에는 화면 아래에 주요 메뉴를 표시한다.
        if (isLoggedIn) {
            DashboardBottomMenu(
                onOpenShopping = { onOpenShopping(false) }
            )
        }
    }
}

/**
 * 로그인한 사용자의 대시보드 요약 영역이다.
 * 장보기에서 돌아오면 다시 생성되어 최신 건수를 조회한다.
 */
@Composable
private fun DashboardSummary(
    onOpenShopping: (Boolean) -> Unit
) {
    // null은 아직 조회하지 않은 상태이며, 0은 실제 조회 결과다.
    var pendingCount by remember { mutableStateOf<Long?>(null) }
    var completedCount by remember { mutableStateOf<Long?>(null) }

    // 조회 중 여부, 실패 여부, 재조회 횟수를 관리한다.
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadCount by remember { mutableStateOf(0) }

    // 처음 표시될 때와 다시 시도 버튼을 누를 때 서버를 조회한다.
    LaunchedEffect(reloadCount) {
        loading = true
        loadFailed = false

        try {
            // 목록 전체를 가져오지 않고 첫 페이지의 전체 건수를 사용한다.
            val pending = ShoppingApi.fetchShoppingItems(
                isCompleted = false,
                page = 1
            )

            val completed = ShoppingApi.fetchShoppingItems(
                isCompleted = true,
                page = 1
            )

            // 두 요청이 성공한 뒤 건수를 함께 반영한다.
            pendingCount = pending.totalCount
            completedCount = completed.totalCount
        } catch (e: CancellationException) {
            // 화면 이동으로 취소된 요청은 취소 상태를 유지한다.
            throw e
        } catch (e: Exception) {
            // 실패한 결과를 0건으로 표시하지 않도록 별도로 관리한다.
            loadFailed = true
        } finally {
            loading = false
        }
    }

    // 생활비 API 연결 전임을 명확히 표시한다.
    DashboardPendingCard(
        title = "이번 달 생활비",
        description = "월별 지출 합계 · 연동 예정"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "장보기",
            color = DashboardNavy,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // 두 카드를 같은 너비로 배치한다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShoppingSummaryCard(
                title = "살 물품",
                count = pendingCount,
                loading = loading,
                failed = loadFailed,
                backgroundColor = DashboardNavy,
                textColor = Color.White,
                onClick = { onOpenShopping(false) },
                modifier = Modifier.weight(1f)
            )

            ShoppingSummaryCard(
                title = "구매 완료",
                count = completedCount,
                loading = loading,
                failed = loadFailed,
                backgroundColor = DashboardLightBlue,
                textColor = DashboardNavy,
                onClick = { onOpenShopping(true) },
                modifier = Modifier.weight(1f)
            )
        }

        // 조회 실패 시 안내와 재시도 버튼을 표시한다.
        if (loadFailed) {
            Text(
                text = "장보기 건수를 불러오지 못했습니다.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )

            TextButton(
                onClick = { reloadCount++ }
            ) {
                Text("다시 시도")
            }
        }
    }

    // 일정 API를 연결할 위치를 표시한다.
    DashboardPendingCard(
        title = "다가오는 일정",
        description = "예정된 생활 일정 · 연동 예정"
    )
}

/**
 * 장보기 상태별 건수를 표시하는 카드다.
 * 카드 전체를 누르면 해당 장보기 탭으로 이동한다.
 */
@Composable
private fun ShoppingSummaryCard(
    title: String,
    count: Long?,
    loading: Boolean,
    failed: Boolean,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = textColor
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            // 실제 건수와 조회 중·실패 상태를 구분한다.
            val countText = when {
                loading -> "조회 중…"
                failed -> "확인 필요"
                count == null -> "—"
                else -> "${count}건"
            }

            Text(
                text = countText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "목록 보기 ›",
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 아직 서버 데이터가 연결되지 않은 요약 영역을 표시한다.
 * 연결 전 상태를 안내하고, 실제 연동 후 내용으로 교체한다.
 */
@Composable
private fun DashboardPendingCard(
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        contentColor = DashboardNavy
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                color = DashboardSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 대시보드 아래에 고정되는 메뉴다.
 * 홈은 현재 위치이며 장보기는 기존 목록 화면으로 이동한다.
 * 생활비와 더보기는 화면 구현 후 클릭 동작을 연결한다.
 */
@Composable
private fun DashboardBottomMenu(
    onOpenShopping: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 현재 화면인 홈을 강조한다.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DashboardLightBlue
                ) {
                    Text(
                        text = "홈",
                        modifier = Modifier.padding(
                            horizontal = 20.dp,
                            vertical = 12.dp
                        ),
                        color = DashboardNavy,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 아직 구현하지 않은 메뉴는 비활성 상태로 표시한다.
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            ) {
                Text("생활비")
            }

            // 장보기 메뉴는 살 물품 탭으로 이동한다.
            TextButton(
                onClick = onOpenShopping,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "장보기",
                    color = DashboardNavy
                )
            }

            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f)
            ) {
                Text("더보기")
            }
        }
    }
}

/**
 * 서버 요청 없이 로그인 전 화면의 디자인을 확인한다.
 */
@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    MyHomeTheme {
        DashboardScreen(
            isLoggedIn = false,
            onLoginSuccess = {},
            onOpenShopping = {}
        )
    }
}