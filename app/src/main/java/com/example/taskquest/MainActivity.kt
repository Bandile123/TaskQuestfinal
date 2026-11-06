package com.example.taskquest

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.taskquest.ui.theme.TASKQUESTTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TASKQUESTTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TaskQuestApp()
                }
            }
        }
    }
}

@Composable
fun TaskQuestApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var showRegister by remember { mutableStateOf(false) }

    if (user == null) {
        if (showRegister) {
            RegisterScreen(
                onRegistered = { user = auth.currentUser },
                onNavigateToLogin = { showRegister = false }
            )
        } else {
            LoginScreen(
                onLoggedIn = { user = auth.currentUser },
                onNavigateToRegister = { showRegister = true }
            )
        }
    } else {
        HomeScreen(
            email = user?.email.orEmpty(),
            onSignOut = {
                auth.signOut()
                user = null
            }
        )
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onNavigateToRegister: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TaskQuest", style = MaterialTheme.typography.headlineLarge)
        Text("Turn your goals into quests.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !loading,
            onClick = {
                if (email.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Enter your email and password.", Toast.LENGTH_SHORT).show()
                } else {
                    loading = true
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            loading = false
                            if (task.isSuccessful) onLoggedIn()
                            else Toast.makeText(context, task.exception?.localizedMessage ?: "Login failed.", Toast.LENGTH_LONG).show()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Please wait..." else "Login") }
        TextButton(onClick = onNavigateToRegister) { Text("Don't have an account? Create one") }
    }
}

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onNavigateToLogin: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Create your account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password (at least 6 characters)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Confirm password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Button(
            enabled = !loading,
            onClick = {
                when {
                    email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                        Toast.makeText(context, "Complete all fields.", Toast.LENGTH_SHORT).show()
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                        Toast.makeText(context, "Enter a valid email address.", Toast.LENGTH_SHORT).show()
                    password.length < 6 ->
                        Toast.makeText(context, "Password must have at least 6 characters.", Toast.LENGTH_SHORT).show()
                    password != confirmPassword ->
                        Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    else -> {
                        loading = true
                        auth.createUserWithEmailAndPassword(email.trim(), password)
                            .addOnCompleteListener { task ->
                                loading = false
                                if (task.isSuccessful) onRegistered()
                                else Toast.makeText(context, task.exception?.localizedMessage ?: "Registration failed.", Toast.LENGTH_LONG).show()
                            }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (loading) "Creating account..." else "Create account") }
        TextButton(onClick = onNavigateToLogin) { Text("Already have an account? Login") }
    }
}

private data class Quest(val id: String, val title: String, val completed: Boolean)

@Composable
fun HomeScreen(email: String, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }
    var taskTitle by remember { mutableStateOf("") }
    var quests by remember { mutableStateOf(listOf<Quest>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(userId) {
        var registration: ListenerRegistration? = null
        if (userId != null) {
            registration = db.collection("users").document(userId).collection("quests")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        error = exception.localizedMessage ?: "Could not load quests."
                        loading = false
                    } else {
                        quests = snapshot?.documents?.map { doc ->
                            Quest(doc.id, doc.getString("title") ?: "", doc.getBoolean("completed") ?: false)
                        } ?: emptyList()
                        loading = false
                    }
                }
        } else loading = false
        onDispose { registration?.remove() }
    }

    val completedCount = quests.count { it.completed }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TaskQuest", style = MaterialTheme.typography.headlineMedium)
                Text(email, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Your adventure", style = MaterialTheme.typography.titleLarge)
                Text("$completedCount of ${quests.size} quests completed")
                Text("XP earned: ${completedCount * 10}")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Add a new quest", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = taskTitle,
            onValueChange = { taskTitle = it },
            label = { Text("What do you need to do?") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val title = taskTitle.trim()
                if (title.isEmpty()) {
                    Toast.makeText(context, "Enter a task first.", Toast.LENGTH_SHORT).show()
                } else if (userId != null) {
                    db.collection("users").document(userId).collection("quests")
                        .add(mapOf("title" to title, "completed" to false, "createdAt" to System.currentTimeMillis()))
                        .addOnSuccessListener { taskTitle = "" }
                        .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Could not save task.", Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add quest") }
        Spacer(Modifier.height(18.dp))
        Text("My quests", style = MaterialTheme.typography.titleLarge)
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && quests.isEmpty()) Text("No quests yet. Add your first quest above!")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quests, key = { it.id }) { quest ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = quest.completed,
                            onCheckedChange = { checked ->
                                if (userId != null) db.collection("users").document(userId).collection("quests")
                                    .document(quest.id).update("completed", checked)
                            }
                        )
                        Text(
                            quest.title,
                            modifier = Modifier.weight(1f),
                            style = if (quest.completed) MaterialTheme.typography.bodyLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyLarge
                        )
                        TextButton(onClick = {
                            if (userId != null) db.collection("users").document(userId).collection("quests")
                                .document(quest.id).delete()
                        }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
