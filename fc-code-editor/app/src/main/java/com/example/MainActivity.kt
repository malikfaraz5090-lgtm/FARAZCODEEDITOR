package com.example

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Project
import com.example.data.Templates
import com.example.ui.CodeSyntaxHighlighter
import com.example.ui.ProjectViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: ProjectViewModel = viewModel()
    val projects by viewModel.allProjects.collectAsState()

    // Screen State: 0 = Home, 1 = Code Editor, 2 = APK Builder, 3 = Templates
    var currentScreenIndex by remember { mutableStateOf(0) }

    // Dialog state
    var showNewProjectDialog by remember { mutableStateOf(false) }

    // Floating Web Preview Bottom Sheet state
    var showLivePreview by remember { mutableStateOf(false) }

    // Build Success Dialog state
    var showBuildSuccessDialog by remember { mutableStateOf(false) }

    // SAF Launchers for ZIP Export / Import and APK Download
    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.activeProject?.let { project ->
                viewModel.exportProjectZip(context, project, uri)
                Toast.makeText(context, "Project exported successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importProjectZip(context, uri)
            currentScreenIndex = 1 // Switch to Editor
            Toast.makeText(context, "Project imported successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    val apkDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            try {
                val generatedFile = viewModel.generatedApkFile
                if (generatedFile != null && generatedFile.exists()) {
                    java.io.FileInputStream(generatedFile).use { inputStream ->
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "Standalone APK '${viewModel.apkAppName}' downloaded successfully! Install it on your device now.", Toast.LENGTH_LONG).show()
                } else {
                    context.assets.open("runner-debug.apk").use { inputStream ->
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, "Standalone Previewer APK downloaded successfully! Install it on your device now.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to copy APK: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Main Screen Host with crossfade slide animations
        Box(modifier = Modifier.weight(1f)) {
            when (currentScreenIndex) {
                0 -> HomeScreen(
                    projects = projects,
                    onNewProjectClick = { showNewProjectDialog = true },
                    onProjectSelect = { project ->
                        viewModel.selectProject(project)
                        currentScreenIndex = 1
                    },
                    onDeleteProject = { viewModel.deleteProject(it) },
                    onImportClick = { zipImportLauncher.launch("application/zip") },
                    onBuildQuickClick = { project ->
                        viewModel.selectProject(project)
                        viewModel.saveActiveProject()
                        currentScreenIndex = 2
                    },
                    onNavigateTemplates = { currentScreenIndex = 3 }
                )
                1 -> CodeEditorScreen(
                    viewModel = viewModel,
                    onRunClick = { showLivePreview = true },
                    onExportClick = {
                        viewModel.activeProject?.let { proj ->
                            zipExportLauncher.launch("${proj.name.replace(" ", "_")}_source.zip")
                        }
                    },
                    onBuildClick = {
                        viewModel.saveActiveProject()
                        currentScreenIndex = 2
                    },
                    onNavigateHome = { currentScreenIndex = 0 }
                )
                2 -> ApkBuilderScreen(
                    viewModel = viewModel,
                    onBuildClick = {
                        viewModel.saveActiveProject()
                        showBuildSuccessDialog = true
                        viewModel.startApkBuild(context)
                    },
                    onDownloadApkClick = {
                        apkDownloadLauncher.launch("${viewModel.apkAppName.replace(" ", "_")}.apk")
                    }
                )
                3 -> TemplatesScreen(
                    onSelectTemplate = { template ->
                        viewModel.createNewProject(template.name, template.packageName)
                        // Override content with template specifics
                        viewModel.activeProject?.let {
                            viewModel.updateCode("html", template.htmlCode)
                            viewModel.updateCode("css", template.cssCode)
                            viewModel.updateCode("js", template.jsCode)
                            viewModel.saveActiveProject()
                        }
                        currentScreenIndex = 1 // Go to Editor
                        Toast.makeText(context, "Created ${template.name}!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Custom Bottom Navigation Bar
        NavigationBar(
            containerColor = VsCodeSidebar,
            modifier = Modifier.testTag("bottom_navigation_bar")
        ) {
            NavigationBarItem(
                selected = currentScreenIndex == 0,
                onClick = { currentScreenIndex = 0 },
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = VsCodeAccent,
                    indicatorColor = VsCodeAccent,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = currentScreenIndex == 1,
                onClick = { currentScreenIndex = 1 },
                icon = { Icon(Icons.Filled.Code, contentDescription = "Code Editor") },
                label = { Text("Editor", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = VsCodeAccent,
                    indicatorColor = VsCodeAccent,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = currentScreenIndex == 2,
                onClick = { currentScreenIndex = 2 },
                icon = { Icon(Icons.Filled.Build, contentDescription = "APK Builder") },
                label = { Text("Builder", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = VsCodeAccent,
                    indicatorColor = VsCodeAccent,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
            NavigationBarItem(
                selected = currentScreenIndex == 3,
                onClick = { currentScreenIndex = 3 },
                icon = { Icon(Icons.Filled.Dashboard, contentDescription = "Templates") },
                label = { Text("Templates", fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.Black,
                    selectedTextColor = VsCodeAccent,
                    indicatorColor = VsCodeAccent,
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        NewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, pkg ->
                viewModel.createNewProject(name, pkg)
                showNewProjectDialog = false
                currentScreenIndex = 1 // Go to editor
            }
        )
    }

    // Build Success Dialog
    if (showBuildSuccessDialog && viewModel.buildStatus == "SUCCESS") {
        AlertDialog(
            onDismissRequest = { showBuildSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = VsCodeAccent,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Custom Signed APK Ready!", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your custom, cryptographically signed standalone APK '${viewModel.apkAppName}' has been compiled successfully with your specific name, offline source code, and custom launcher icon!",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    
                    // Warning/Info Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF81C784),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "How to run your custom code:",
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Click 'Download APK' below to get the installer package.\n2. Install the APK on your device (allow 'Unknown Sources' if asked).\n3. Open the installed '${viewModel.apkAppName}' app—it runs completely standalone, 100% offline, showing your custom brand, name, and launcher logo!",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBuildSuccessDialog = false
                        apkDownloadLauncher.launch("${viewModel.apkAppName.replace(" ", "_")}.apk")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Download APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuildSuccessDialog = false }) {
                    Text("Dismiss", color = Color.Gray, fontSize = 12.sp)
                }
            },
            containerColor = VsCodeSidebar,
            shape = RoundedCornerShape(12.dp)
        )
    }

    // Live Web Preview Popup Modal
    if (showLivePreview) {
        Dialog(onDismissRequest = { showLivePreview = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                color = if (viewModel.isPreviewDarkTheme) Color(0xFF121212) else Color.White
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Browser Bar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (viewModel.isPreviewDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF3F3F3))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5F56))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFBD2E))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF27C93F))
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // URL Bar Preview
                        Row(
                            modifier = Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (viewModel.isPreviewDarkTheme) Color(0xFF0F0F0F) else Color.White)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Secure",
                                tint = Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "localhost:3000",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Dark/Light Theme preview toggle
                        IconButton(
                            onClick = { viewModel.isPreviewDarkTheme = !viewModel.isPreviewDarkTheme },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (viewModel.isPreviewDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Preview Theme",
                                tint = if (viewModel.isPreviewDarkTheme) Color.Yellow else Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { showLivePreview = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close Preview",
                                tint = if (viewModel.isPreviewDarkTheme) Color.LightGray else Color.DarkGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Native WebView rendering offline HTML/CSS/JS code
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                }
                            },
                            update = { webView ->
                                val combinedHtml = viewModel.getCombinedHtml()
                                webView.loadDataWithBaseURL(
                                    "https://local.code2apk.studio/",
                                    combinedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 1: HOME SCREEN
// -------------------------------------------------------------
@Composable
fun HomeScreen(
    projects: List<Project>,
    onNewProjectClick: () -> Unit,
    onProjectSelect: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onImportClick: () -> Unit,
    onBuildQuickClick: (Project) -> Unit,
    onNavigateTemplates: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_code_apk_logo),
                contentDescription = "FC Code Studio Logo",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "FC Code Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
                Text(
                    text = "Write Web Code. Run Live Previews & Create APKs.",
                    fontSize = 11.sp,
                    color = VsCodeAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero Panel Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2D2D2D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Build Native Android Apps Directly on Device",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "FC Code Studio wraps your custom offline index.html, style.css and script.js files in a high-performance sandboxed WebView, with instant split-screen previews and fully working installable APKs.",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Main Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onNewProjectClick,
                colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("new_project_button")
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Project", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF444444)),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("import_zip_button")
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import ZIP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Projects List Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "My Projects (${projects.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            TextButton(onClick = onNavigateTemplates) {
                Text("See Starter Templates", color = VsCodeAccent, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No projects yet", color = Color.Gray, fontSize = 13.sp)
                    TextButton(onClick = onNewProjectClick) {
                        Text("Create your first project", color = VsCodeAccent, fontSize = 13.sp)
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projects.forEach { project ->
                    ProjectItemCard(
                        project = project,
                        onClick = { onProjectSelect(project) },
                        onDeleteClick = { onDeleteProject(project) },
                        onBuildClick = { onBuildQuickClick(project) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectItemCard(
    project: Project,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBuildClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF252526))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated Adaptive App Icon representation
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(project.iconColor)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconVector(project.iconSymbol),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = project.packageName,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Small Fast Compile Action Button
            IconButton(
                onClick = onBuildClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = "Build APK",
                    tint = VsCodeAccent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Delete trigger
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete Project",
                    tint = Color(0xFFFF5F56),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 2: CODE EDITOR SCREEN
// -------------------------------------------------------------
@Composable
fun CodeEditorScreen(
    viewModel: ProjectViewModel,
    onRunClick: () -> Unit,
    onExportClick: () -> Unit,
    onBuildClick: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val activeProject = viewModel.activeProject
    val context = LocalContext.current

    if (activeProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VsCodeBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No Project Selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    "Open an existing project from the Home tab or select a template to start coding.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onNavigateHome,
                    colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent)
                ) {
                    Text("Go to Home", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Capture current editor values for sync
    var editorValue by remember(viewModel.activeProject, viewModel.activeTab) {
        val rawText = when (viewModel.activeTab) {
            "html" -> viewModel.htmlContent
            "css" -> viewModel.cssContent
            "js" -> viewModel.jsContent
            else -> ""
        }
        mutableStateOf(TextFieldValue(rawText))
    }

    var isSplitPreviewEnabled by remember { mutableStateOf(false) }
    var isSplitPreviewMaximized by remember { mutableStateOf(false) }
    var isPreviewDarkTheme by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeBg)
    ) {
        // Toolbar with Save, Undo, Redo, Run
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VsCodeSidebar)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project Title Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(activeProject.iconColor).copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activeProject.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(activeProject.iconColor)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Undo
            IconButton(onClick = {
                viewModel.undo()
                val restored = when (viewModel.activeTab) {
                    "html" -> viewModel.htmlContent
                    "css" -> viewModel.cssContent
                    "js" -> viewModel.jsContent
                    else -> ""
                }
                editorValue = TextFieldValue(restored)
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = Color.White)
            }

            // Redo
            IconButton(onClick = {
                viewModel.redo()
                val restored = when (viewModel.activeTab) {
                    "html" -> viewModel.htmlContent
                    "css" -> viewModel.cssContent
                    "js" -> viewModel.jsContent
                    else -> ""
                }
                editorValue = TextFieldValue(restored)
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = Color.White)
            }

            // Find and Replace panel toggle
            IconButton(
                onClick = { viewModel.showFindReplace = !viewModel.showFindReplace },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Find & Replace",
                    tint = if (viewModel.showFindReplace) VsCodeAccent else Color.White
                )
            }

            // Save trigger
            IconButton(onClick = {
                viewModel.saveActiveProject()
                Toast.makeText(context, "Project Saved Locally!", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Save, contentDescription = "Save Project", tint = Color.White)
            }

            // Share ZIP Export
            IconButton(onClick = onExportClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.CloudDownload, contentDescription = "Export ZIP", tint = Color.White)
            }

            // Toggle Split Live Preview
            IconButton(
                onClick = {
                    isSplitPreviewEnabled = !isSplitPreviewEnabled
                    if (!isSplitPreviewEnabled) {
                        isSplitPreviewMaximized = false
                    }
                },
                modifier = Modifier.size(36.dp).testTag("toggle_split_preview")
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Toggle Split Preview",
                    tint = if (isSplitPreviewEnabled) VsCodeAccent else Color.White
                )
            }

            // Build Standalone APK
            Button(
                onClick = onBuildClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("editor_build_apk_button")
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("BUILD APK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Run Code Live Web Preview
            Button(
                onClick = onRunClick,
                colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("run_preview_button")
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("RUN", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        // Inline Find & Replace Panel (vs-code style)
        AnimatedVisibility(
            visible = viewModel.showFindReplace,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VsCodeSidebar)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .border(1.dp, Color(0xFF2D2D2D)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = viewModel.findQuery,
                        onValueChange = { viewModel.findQuery = it },
                        placeholder = { Text("Find text...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = TextStyle(fontSize = 12.sp),
                        singleLine = true
                    )

                    TextField(
                        value = viewModel.replaceQuery,
                        onValueChange = { viewModel.replaceQuery = it },
                        placeholder = { Text("Replace with...", fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF1E1E1E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = TextStyle(fontSize = 12.sp),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.findQuery = ""; viewModel.replaceQuery = "" }) {
                        Text("Clear", color = Color.Gray, fontSize = 11.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.performFindReplace()
                            // Sync state
                            val replacedText = when (viewModel.activeTab) {
                                "html" -> viewModel.htmlContent
                                "css" -> viewModel.cssContent
                                "js" -> viewModel.jsContent
                                else -> ""
                            }
                            editorValue = TextFieldValue(replacedText)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D2D2D)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Replace All", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }

        // Tabs Headers: html, css, js
        TabRow(
            selectedTabIndex = when (viewModel.activeTab) {
                "html" -> 0
                "css" -> 1
                "js" -> 2
                else -> 0
            },
            containerColor = VsCodeBg,
            contentColor = VsCodeAccent,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[when (viewModel.activeTab) {
                        "html" -> 0
                        "css" -> 1
                        "js" -> 2
                        else -> 0
                    }]),
                    color = VsCodeAccent
                )
            }
        ) {
            Tab(
                selected = viewModel.activeTab == "html",
                onClick = { viewModel.activeTab = "html" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌐 index.html", color = if (viewModel.activeTab == "html") Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = viewModel.activeTab == "css",
                onClick = { viewModel.activeTab = "css" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎨 style.css", color = if (viewModel.activeTab == "css") Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = viewModel.activeTab == "js",
                onClick = { viewModel.activeTab = "js" },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ script.js", color = if (viewModel.activeTab == "js") Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Monospace code text area with scrollable synced line numbers, with optional vertical split live preview
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (!isSplitPreviewMaximized) {
                Box(
                    modifier = Modifier
                        .weight(if (isSplitPreviewEnabled) 1.2f else 1f)
                        .fillMaxWidth()
                        .background(VsCodeEditorBg)
                ) {
                    val lineCount = editorValue.text.split("\n").size
                    val lineNumbersText = (1..lineCount).joinToString("\n") { it.toString().padStart(2, '0') }

                    val verticalScrollState = rememberScrollState()
                    val horizontalScrollState = rememberScrollState()

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        // Left Column: Line Numbers
                        Text(
                            text = lineNumbersText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .verticalScroll(verticalScrollState)
                                .padding(end = 12.dp, start = 4.dp)
                        )

                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color(0xFF2D2D2D))
                        )

                        // Right Column: BasicTextField
                        BasicTextField(
                            value = editorValue,
                            onValueChange = {
                                editorValue = it
                                viewModel.updateCode(viewModel.activeTab, it.text)
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = Color.White
                            ),
                            visualTransformation = CodeSyntaxHighlighter(viewModel.activeTab),
                            cursorBrush = SolidColor(VsCodeAccent),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp)
                                .verticalScroll(verticalScrollState)
                                .horizontalScroll(horizontalScrollState)
                                .weight(1f)
                                .testTag("code_text_field")
                        )
                    }
                }
            }

            if (isSplitPreviewEnabled) {
                if (!isSplitPreviewMaximized) {
                    // Border/Divider line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(VsCodeAccent)
                    )
                }

                // Split Live Preview Panel
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(if (isPreviewDarkTheme) Color(0xFF121212) else Color.White)
                ) {
                    // Mini Toolbar for preview panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isPreviewDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF3F3F3))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSplitPreviewMaximized) "FULL SCREEN LIVE PREVIEW" else "LIVE SPLIT PREVIEW",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPreviewDarkTheme) Color.LightGray else Color.DarkGray
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        // Maximize/Split Toggle
                        IconButton(
                            onClick = { isSplitPreviewMaximized = !isSplitPreviewMaximized },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isSplitPreviewMaximized) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isSplitPreviewMaximized) "Restore Split Screen" else "Maximize Preview to Fullscreen",
                                tint = if (isPreviewDarkTheme) Color.White else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { isPreviewDarkTheme = !isPreviewDarkTheme },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = if (isPreviewDarkTheme) Color.Yellow else Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // WebView inside AndroidView
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    webChromeClient = WebChromeClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                }
                            },
                            update = { webView ->
                                val combinedHtml = viewModel.getCombinedHtml()
                                webView.loadDataWithBaseURL(
                                    "https://local.code2apk.studio/",
                                    combinedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Code Editor Quick Symbols accessory keyboard helper row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VsCodeSidebar)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickSymbols = listOf(
                "<", ">", "class=\"\"", "id=\"\"", "{", "}", "(", ")", ";", "=", "\"", "'", "/", "+", "function", "document.querySelector"
            )

            quickSymbols.forEach { symbol ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2D2D2D))
                        .clickable {
                            val selection = editorValue.selection
                            val text = editorValue.text
                            val newText = text.substring(0, selection.start) + symbol + text.substring(selection.end)
                            val newSelection = TextRange(selection.start + symbol.length)
                            editorValue = TextFieldValue(newText, newSelection)
                            viewModel.updateCode(viewModel.activeTab, newText)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = symbol,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VsCodeAccent
                    )
                }
            }
        }
    }
}

fun pinProjectShortcut(context: android.content.Context, project: Project) {
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val size = 192
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        
        var logoDrawn = false
        if (!project.customIconBase64.isNullOrEmpty()) {
            try {
                val bytes = android.util.Base64.decode(project.customIconBase64, android.util.Base64.DEFAULT)
                val img = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (img != null) {
                    val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
                    val rx = when (project.iconShape) {
                        "Round" -> size / 2f
                        "Square" -> 12f
                        "Squircle" -> size * 0.3f
                        "Teardrop" -> size * 0.25f
                        else -> size * 0.2f
                    }
                    
                    val path = android.graphics.Path()
                    if (project.iconShape == "Teardrop") {
                        path.addRoundRect(rect, floatArrayOf(rx, rx, rx, rx, rx, rx, 4f, 4f), android.graphics.Path.Direction.CW)
                    } else {
                        path.addRoundRect(rect, rx, rx, android.graphics.Path.Direction.CW)
                    }
                    
                    canvas.save()
                    canvas.clipPath(path)
                    val srcRect = android.graphics.Rect(0, 0, img.width, img.height)
                    val dstRect = android.graphics.Rect(0, 0, size, size)
                    canvas.drawBitmap(img, srcRect, dstRect, paint)
                    canvas.restore()
                    logoDrawn = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        if (!logoDrawn) {
            paint.color = project.iconColor
            val rect = android.graphics.RectF(0f, 0f, size.toFloat(), size.toFloat())
            val rx = when (project.iconShape) {
                "Round" -> size / 2f
                "Square" -> 12f
                "Squircle" -> size * 0.3f
                "Teardrop" -> size * 0.25f
                else -> size * 0.2f
            }
            if (project.iconShape == "Teardrop") {
                val path = android.graphics.Path()
                path.addRoundRect(rect, floatArrayOf(rx, rx, rx, rx, rx, rx, 4f, 4f), android.graphics.Path.Direction.CW)
                canvas.drawPath(path, paint)
            } else {
                canvas.drawRoundRect(rect, rx, rx, paint)
            }
            
            paint.color = android.graphics.Color.WHITE
            paint.textAlign = android.graphics.Paint.Align.CENTER
            paint.textSize = size * 0.45f
            paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            val letter = if (project.name.isNotEmpty()) project.name.substring(0, 1).uppercase() else "F"
            val fontMetrics = paint.fontMetrics
            val x = size / 2f
            val y = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(letter, x, y, paint)
        }
        
        val shortcutIntent = android.content.Intent().apply {
            setClassName("com.fccode.runner", "com.fccode.runner.MainActivity")
            action = android.content.Intent.ACTION_MAIN
            putExtra("project_id", project.id)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        
        val shortcutInfo = ShortcutInfoCompat.Builder(context, "project_shortcut_${project.id}")
            .setShortLabel(project.name)
            .setLongLabel(project.name)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(shortcutIntent)
            .build()
            
        val success = ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        if (success) {
            android.widget.Toast.makeText(context, "'${project.name}' added to Home Screen!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            android.widget.Toast.makeText(context, "Failed to pin shortcut on your launcher", android.widget.Toast.LENGTH_LONG).show()
        }
    } else {
        android.widget.Toast.makeText(context, "Launcher shortcut is not supported", android.widget.Toast.LENGTH_LONG).show()
    }
}

// -------------------------------------------------------------
// SCREEN 3: APK BUILDER SCREEN
// -------------------------------------------------------------
@Composable
fun ApkBuilderScreen(
    viewModel: ProjectViewModel,
    onBuildClick: () -> Unit,
    onDownloadApkClick: () -> Unit
) {
    val activeProject = viewModel.activeProject
    val context = LocalContext.current

    if (activeProject == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VsCodeBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Outlined.Build,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No Project Open",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Text(
                    "Open a project first from the Home panel to unlock the APK Cloud compiler.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Title block
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Build, contentDescription = null, tint = VsCodeAccent)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cloud APK Builder", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
        Text(
            "Configure Android packaging, manifest specs, and build native WebView wrappers.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (viewModel.buildStatus == "BUILDING") {
            // Build Status Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SIMULATED CLOUD COMPILING...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = VsCodeAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress linear indicator
                    LinearProgressIndicator(
                        progress = { viewModel.buildProgress },
                        color = VsCodeAccent,
                        trackColor = Color(0xFF2D2D2D),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Agent active", fontSize = 11.sp, color = Color.Gray)
                        Text("${(viewModel.buildProgress * 100).toInt()}%", fontSize = 11.sp, color = VsCodeAccent, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrolled terminal console logs
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D0E12))
                            .border(1.dp, Color(0xFF252526))
                            .padding(10.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(viewModel.buildLogs) { log ->
                                Text(
                                    text = "> $log",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        } else if (viewModel.buildStatus == "SUCCESS") {
            // Build Successful Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, VsCodeAccent.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(VsCodeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VsCodeAccent, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("FC Previewer APK Compiled!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Text(
                        "Your on-device live testing package is prepared and ready to load your active project.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    // STANDALONE 1-CLICK INSTANT INSTALL SHORTCUT
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
                        border = BorderStroke(1.dp, VsCodeAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Home, contentDescription = null, tint = VsCodeAccent, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DIL PASAND APPNAM & LOGO SHORTCUT",
                                    color = VsCodeAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Instantly pin a real shortcut directly on your mobile's Home Screen with your custom logo (even from your gallery!) and custom name. It launches your project in fullscreen just like a real app!",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Button(
                                onClick = {
                                    activeProject.let { project ->
                                        val updatedProj = project.copy(
                                            name = viewModel.apkAppName,
                                            appVersion = viewModel.apkAppVersion,
                                            companyName = viewModel.apkCompanyName,
                                            orientation = viewModel.apkOrientation,
                                            iconShape = viewModel.apkIconShape,
                                            minSdkVersion = viewModel.apkMinSdkVersion,
                                            enableSplashScreen = viewModel.apkEnableSplashScreen,
                                            splashColor = viewModel.apkSplashColor,
                                            permissionInternet = viewModel.apkPermissionInternet,
                                            permissionCamera = viewModel.apkPermissionCamera,
                                            permissionStorage = viewModel.apkPermissionStorage,
                                            customIconBase64 = viewModel.apkCustomIconBase64
                                        )
                                        pinProjectShortcut(context, updatedProj)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Custom Shortcut to Home Screen", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Explanation Notice
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "How to install and run your app offline:",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Click the 'Download & Install APK' button below.\n2. Save and install the APK on your device (you may need to allow install from files).\n3. Open 'FC Code Previewer' from your launcher—it will dynamically load and run your HTML/CSS/JS project code natively in full screen!",
                                color = Color.White,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = onDownloadApkClick,
                        colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("download_apk_button")
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download & Install APK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = { viewModel.buildStatus = "IDLE" }) {
                        Text("Configure & Compile Again", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Setup compilation parameters
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("APP METADATA", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VsCodeAccent)

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Name Input
                    Text("App Name Launcher Label", fontSize = 12.sp, color = Color.LightGray)
                    OutlinedTextField(
                        value = viewModel.apkAppName,
                        onValueChange = { viewModel.apkAppName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VsCodeAccent
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Package Name Input
                    Text("Android Application Package Name", fontSize = 12.sp, color = Color.LightGray)
                    OutlinedTextField(
                        value = viewModel.apkPackageName,
                        onValueChange = { viewModel.apkPackageName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VsCodeAccent
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("App Version", fontSize = 12.sp, color = Color.LightGray)
                            OutlinedTextField(
                                value = viewModel.apkAppVersion,
                                onValueChange = { viewModel.apkAppVersion = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = VsCodeAccent
                                ),
                                singleLine = true
                            )
                        }
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text("Developer / Company", fontSize = 12.sp, color = Color.LightGray)
                            OutlinedTextField(
                                value = viewModel.apkCompanyName,
                                onValueChange = { viewModel.apkCompanyName = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = VsCodeAccent
                                ),
                                singleLine = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("CUSTOM APP LAUNCHER ICON", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VsCodeAccent)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Preset Icon Colors selection
                    Text("Background Palette Color", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val iconColors = listOf(0xFF00C853, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63, 0xFF9C27B0)
                        iconColors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (viewModel.apkSelectedIconColor == hex.toInt()) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.apkSelectedIconColor = hex.toInt() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Icon Symbols selection
                    Text("Icon Vector Symbol", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val symbols = listOf("code", "calculate", "check_circle", "sports_esports", "person")
                        symbols.forEach { symbol ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (viewModel.apkSelectedIconSymbol == symbol) VsCodeAccent else Color(0xFF2D2D2D))
                                    .clickable { viewModel.apkSelectedIconSymbol = symbol }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getIconVector(symbol),
                                    contentDescription = null,
                                    tint = if (viewModel.apkSelectedIconSymbol == symbol) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // --- Custom Gallery Icon Selection ---
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Or Select Custom App Logo from Gallery", fontSize = 12.sp, color = Color.LightGray)
                    
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val imagePickerLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            try {
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    val bytes = inputStream.readBytes()
                                    if (bytes.size > 1024 * 1024) {
                                        // Compress if it is too large (> 1MB)
                                        val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        val outputStream = java.io.ByteArrayOutputStream()
                                        originalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
                                        val compressedBytes = outputStream.toByteArray()
                                        val base64 = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.DEFAULT)
                                        viewModel.apkCustomIconBase64 = base64
                                    } else {
                                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                                        viewModel.apkCustomIconBase64 = base64
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val customBitmap = remember(viewModel.apkCustomIconBase64) {
                            viewModel.apkCustomIconBase64?.let { base64 ->
                                try {
                                    val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }

                        val shapeClip = when (viewModel.apkIconShape) {
                            "Squircle" -> RoundedCornerShape(12.dp)
                            "Round" -> CircleShape
                            "Square" -> RoundedCornerShape(2.dp)
                            "Teardrop" -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 2.dp)
                            else -> RoundedCornerShape(8.dp)
                        }

                        if (customBitmap != null) {
                            Image(
                                bitmap = customBitmap.asImageBitmap(),
                                contentDescription = "Custom Launcher Icon Preview",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(shapeClip)
                                    .border(1.5.dp, VsCodeAccent, shapeClip)
                            )
                        } else {
                            // Render a nice preview of the generated fallback icon with the color and symbol
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(shapeClip)
                                    .background(Color(viewModel.apkSelectedIconColor)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getIconVector(viewModel.apkSelectedIconSymbol),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (customBitmap != null) "Change Logo" else "Choose Gallery Image", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (customBitmap != null) {
                                TextButton(
                                    onClick = { viewModel.apkCustomIconBase64 = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Remove Custom Logo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Launcher Icon Adaptive Shape", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val shapes = listOf("Squircle", "Round", "Square", "Teardrop")
                        shapes.forEach { shape ->
                            val shapeClip = when (shape) {
                                "Squircle" -> RoundedCornerShape(10.dp)
                                "Round" -> CircleShape
                                "Square" -> RoundedCornerShape(2.dp)
                                "Teardrop" -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 2.dp)
                                else -> RoundedCornerShape(8.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(shapeClip)
                                    .background(if (viewModel.apkIconShape == shape) VsCodeAccent else Color(0xFF2D2D2D))
                                    .border(
                                        width = if (viewModel.apkIconShape == shape) 1.dp else 0.dp,
                                        color = Color.White,
                                        shape = shapeClip
                                    )
                                    .clickable { viewModel.apkIconShape = shape }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shape,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.apkIconShape == shape) Color.Black else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("ADVANCED DEPLOYMENT OPTIONS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VsCodeAccent)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Default Device Orientation", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val orientations = listOf("Portrait", "Landscape", "Sensor / Auto")
                        orientations.forEach { orient ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (viewModel.apkOrientation == orient) VsCodeAccent else Color(0xFF2D2D2D))
                                    .clickable { viewModel.apkOrientation = orient },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = orient,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.apkOrientation == orient) Color.Black else Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Minimum Android Version Support", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val minSdks = listOf("Android 8.0 (API 26)", "Android 10.0 (API 29)", "Android 13.0 (API 33)")
                        minSdks.forEach { sdk ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (viewModel.apkMinSdkVersion == sdk) VsCodeAccent else Color(0xFF2D2D2D))
                                    .clickable { viewModel.apkMinSdkVersion = sdk }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sdk.replace("Android ", "A"),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.apkMinSdkVersion == sdk) Color.Black else Color.White,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("APP SPLASH SCREEN", fontSize = 12.sp, color = Color.LightGray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (viewModel.apkEnableSplashScreen) "Enabled" else "Disabled",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = viewModel.apkEnableSplashScreen,
                            onCheckedChange = { viewModel.apkEnableSplashScreen = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = VsCodeAccent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0xFF2D2D2D)
                            )
                        )
                    }

                    if (viewModel.apkEnableSplashScreen) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Splash Background Color", fontSize = 12.sp, color = Color.LightGray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val splashColors = listOf(0xFF1E1E1E, 0xFF000000, 0xFF212121, 0xFFFFFFFF, 0xFF0D47A1)
                            splashColors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .border(
                                            width = if (viewModel.apkSplashColor == hex.toInt()) 2.dp else 0.dp,
                                            color = Color.White,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.apkSplashColor = hex.toInt() }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("SYSTEM PERMISSIONS CONFIG", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VsCodeAccent)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Internet Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Internet Access Permission", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Allows app to load external images, video streams and REST APIs.", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = viewModel.apkPermissionInternet,
                            onCheckedChange = { viewModel.apkPermissionInternet = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VsCodeAccent,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                    }

                    // Camera Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Camera Access Permission", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Required for HTML5 web-cam, QR scanners and photo captures.", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = viewModel.apkPermissionCamera,
                            onCheckedChange = { viewModel.apkPermissionCamera = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VsCodeAccent,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                    }

                    // Storage Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Local Storage Read/Write", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("Required to download files or read user media files.", fontSize = 10.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = viewModel.apkPermissionStorage,
                            onCheckedChange = { viewModel.apkPermissionStorage = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = VsCodeAccent,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onBuildClick,
                        colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("compile_apk_button")
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1-CLICK BUILD APK", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 4: starter TEMPLATES SCREEN
// -------------------------------------------------------------
@Composable
fun TemplatesScreen(
    onSelectTemplate: (Project) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VsCodeBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Dashboard, contentDescription = null, tint = VsCodeAccent)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Starter Templates", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        }
        Text(
            "Bootstrapping templates containing comprehensive interactive source files in 1 click.",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        val templates = listOf(
            Templates.Portfolio to "A stunning, dark-mode personal hub containing social cards, responsive layout grids, and interactive components.",
            Templates.Calculator to "A sleek, glowing neon arithmetic web app featuring custom touch click responsive grid arrays.",
            Templates.Todo to "Full todo list storing items utilizing client-side localStorage to persist tasks offline securely.",
            Templates.RetroGame to "A fully interactive classic retro Arcade Snake Game rendered cleanly on a HTML5 high-speed canvas."
        )

        templates.forEach { (template, desc) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = VsCodeSidebar),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF2D2D2D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(template.iconColor).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                getIconVector(template.iconSymbol),
                                contentDescription = null,
                                tint = Color(template.iconColor),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = template.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onSelectTemplate(template) },
                        colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create from Template", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// NEW PROJECT CREATOR DIALOG
// -------------------------------------------------------------
@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pkg: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("com.code2apk.myapp") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Project", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Project Name", color = Color.LightGray, fontSize = 12.sp)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("My Super App") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = VsCodeAccent
                    ),
                    singleLine = true
                )

                Text("Package Namespace (Lower unique ID)", color = Color.LightGray, fontSize = 12.sp)
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it },
                    placeholder = { Text("com.example.app") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = VsCodeAccent
                    ),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, pkg) },
                colors = ButtonDefaults.buttonColors(containerColor = VsCodeAccent)
            ) {
                Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = VsCodeSidebar
    )
}

// Utility mapper to grab relevant vectors cleanly
fun getIconVector(symbol: String): ImageVector {
    return when (symbol) {
        "code" -> Icons.Filled.Code
        "calculate" -> Icons.Filled.Calculate
        "check_circle" -> Icons.Filled.CheckCircle
        "sports_esports" -> Icons.Filled.SportsEsports
        "person" -> Icons.Filled.Person
        "cloud_download" -> Icons.Filled.CloudDownload
        else -> Icons.Filled.Android
    }
}
