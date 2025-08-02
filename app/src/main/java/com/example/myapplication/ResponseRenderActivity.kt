package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

// 新建文件 ResponseRenderActivity.kt
class ResponseRenderActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_HTML_CONTENT = "html_content"

        // 创建启动此Activity的Intent
        fun newIntent(context: Context, htmlContent: String): Intent {
            return Intent(context, ResponseRenderActivity::class.java).apply {
                putExtra(EXTRA_HTML_CONTENT, htmlContent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_response_render)

        // 获取传递的HTML内容
        val htmlContent = intent.getStringExtra(EXTRA_HTML_CONTENT) ?: return

        // 设置WebView
        val webView = findViewById<WebView>(R.id.webView)
        webView.settings.javaScriptEnabled = true

        // 加载HTML内容
        webView.loadDataWithBaseURL(
            null,
            htmlContent,
            "text/html",
            "UTF-8",
            null
        )
    }
}