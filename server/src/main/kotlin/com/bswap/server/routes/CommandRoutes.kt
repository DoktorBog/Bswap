package com.bswap.server.routes

import com.bswap.server.command.CommandProcessor
import com.bswap.shared.model.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

@Serializable
data class CommandRequest(
    val command: String
)

@Serializable 
data class CommandResponse(
    val success: Boolean,
    val message: String,
    val data: String? = null
)

fun Route.commandRoutes(commandProcessor: CommandProcessor) {
    
    // Execute a command
    post("/command") {
        try {
            val request = call.receive<CommandRequest>()
            val result = commandProcessor.processCommand(request.command)
            
            call.respond(
                CommandResponse(
                    success = result.success,
                    message = result.message,
                    data = result.data?.toString()
                )
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiError("Invalid command request: ${e.message}")
            )
        }
    }
    
    // Get command help
    get("/command/help") {
        val result = commandProcessor.processCommand("help")
        call.respond(
            CommandResponse(
                success = result.success,
                message = result.message
            )
        )
    }
    
    // Serve command interface web page
    get("/command-ui") {
        call.respondText(commandInterfaceHtml, io.ktor.http.ContentType.Text.Html)
    }
}

private val commandInterfaceHtml = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bswap Bot Command Interface</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            background: #1a1a1a;
            color: #e0e0e0;
            height: 100vh;
            display: flex;
            flex-direction: column;
        }
        
        .header {
            background: #2d2d2d;
            padding: 1rem;
            border-bottom: 1px solid #444;
            text-align: center;
        }
        
        .header h1 {
            color: #61dafb;
            font-size: 1.2rem;
            font-weight: normal;
        }
        
        .terminal {
            flex: 1;
            display: flex;
            flex-direction: column;
            padding: 1rem;
            background: #1a1a1a;
            overflow: hidden;
        }
        
        .output {
            flex: 1;
            overflow-y: auto;
            margin-bottom: 1rem;
            padding: 0.5rem;
            background: #0d1117;
            border: 1px solid #30363d;
            border-radius: 6px;
            white-space: pre-wrap;
            font-size: 14px;
            line-height: 1.4;
        }
        
        .input-container {
            display: flex;
            align-items: center;
            background: #21262d;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 0.5rem;
        }
        
        .prompt {
            color: #58a6ff;
            margin-right: 0.5rem;
            user-select: none;
        }
        
        .command-input {
            flex: 1;
            background: transparent;
            border: none;
            color: #e0e0e0;
            font-family: inherit;
            font-size: 14px;
            outline: none;
        }
        
        .loading {
            color: #f78c6c;
        }
        
        .success {
            color: #9ccc65;
        }
        
        .error {
            color: #f48771;
        }
        
        .command {
            color: #82aaff;
        }
        
        .timestamp {
            color: #546e7a;
            font-size: 12px;
        }
        
        .help-hint {
            color: #666;
            font-size: 12px;
            margin-top: 0.5rem;
            text-align: center;
        }
        
        ::-webkit-scrollbar {
            width: 8px;
        }
        
        ::-webkit-scrollbar-track {
            background: #1a1a1a;
        }
        
        ::-webkit-scrollbar-thumb {
            background: #444;
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
            background: #555;
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>🤖 Bswap Bot Command Interface</h1>
    </div>
    
    <div class="terminal">
        <div class="output" id="output">
            <div class="success">🚀 Bswap Bot Command Interface Ready</div>
            <div class="timestamp">Connected at: <script>document.write(new Date().toLocaleString())</script></div>
            <div style="margin-top: 1rem;">Type <span class="command">help</span> to see available commands.</div>
            <div style="margin-bottom: 1rem;"></div>
        </div>
        
        <div class="input-container">
            <div class="prompt">bswap$</div>
            <input type="text" class="command-input" id="commandInput" 
                   placeholder="Enter command..." autocomplete="off">
        </div>
        
        <div class="help-hint">
            Press Enter to execute • Type 'help' for commands • Type 'clear' to clear screen
        </div>
    </div>

    <script>
        const output = document.getElementById('output');
        const commandInput = document.getElementById('commandInput');
        let commandHistory = [];
        let historyIndex = -1;
        
        // Focus input on page load
        commandInput.focus();
        
        // Command history navigation
        commandInput.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowUp') {
                e.preventDefault();
                if (historyIndex < commandHistory.length - 1) {
                    historyIndex++;
                    commandInput.value = commandHistory[commandHistory.length - 1 - historyIndex] || '';
                }
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                if (historyIndex > 0) {
                    historyIndex--;
                    commandInput.value = commandHistory[commandHistory.length - 1 - historyIndex] || '';
                } else if (historyIndex === 0) {
                    historyIndex = -1;
                    commandInput.value = '';
                }
            } else if (e.key === 'Enter') {
                executeCommand();
            }
        });
        
        function addToOutput(content, className = '') {
            const div = document.createElement('div');
            if (className) div.className = className;
            div.innerHTML = content;
            output.appendChild(div);
            output.scrollTop = output.scrollHeight;
        }
        
        function executeCommand() {
            const command = commandInput.value.trim();
            if (!command) return;
            
            // Add to history
            if (command !== commandHistory[commandHistory.length - 1]) {
                commandHistory.push(command);
            }
            historyIndex = -1;
            
            // Show command in output
            addToOutput('<span class="prompt">bswap$</span> <span class="command">' + command + '</span>');
            
            // Clear input
            commandInput.value = '';
            
            // Handle local commands
            if (command === 'clear') {
                output.innerHTML = `
                    <div class="success">🧹 Screen cleared</div>
                    <div style="margin-bottom: 1rem;"></div>
                `;
                return;
            }
            
            // Show loading
            const loadingDiv = document.createElement('div');
            loadingDiv.className = 'loading';
            loadingDiv.textContent = '⏳ Executing command...';
            output.appendChild(loadingDiv);
            output.scrollTop = output.scrollHeight;
            
            // Execute remote command
            fetch('/command', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ command: command })
            })
            .then(response => response.json())
            .then(data => {
                // Remove loading indicator
                loadingDiv.remove();
                
                // Add result
                const className = data.success ? 'success' : 'error';
                addToOutput(data.message, className);
                
                if (data.data) {
                    addToOutput(data.data);
                }
                
                // Add timestamp
                addToOutput('<div class="timestamp">Completed at: ' + new Date().toLocaleString() + '</div>');
                addToOutput(''); // Empty line for spacing
            })
            .catch(error => {
                loadingDiv.remove();
                addToOutput('❌ Network error: ' + error.message, 'error');
                addToOutput(''); // Empty line for spacing
            });
        }
        
        // Keep input focused
        document.addEventListener('click', () => {
            if (document.activeElement !== commandInput) {
                commandInput.focus();
            }
        });
        
        // Auto-focus when page becomes visible
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                commandInput.focus();
            }
        });
    </script>
</body>
</html>
""".trimIndent()