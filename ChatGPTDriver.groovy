/**
 * ChatGPT Driver for Hubitat
 * 
 * Version 1.0
 * 
 * This driver allows the Hubitat system to communicate with OpenAI's GPT models and 
 * retrieve responses based on user questions. Ensure you have a valid API key before using.
 */
 
import groovy.json.JsonOutput

metadata {
    definition(name: "ChatGPT Driver", namespace: "chathub", author: "Ethan Frances") {
        capability "Actuator"
        capability "Sensor"
        
        attribute "responseValue", "string"
        attribute "responseAnswer", "string"
        
        attribute "pendingResponse", "bool"
        
        command "ask", [
            [name: "System Content*", type: "STRING", description: "System background details."],
            [name: "User Question*", type: "STRING", description: "The actual request"]
        ]
        
        command "askTrueFalseWithDetail", [
            [name: "User Question*", type: "STRING", description: "The actual request"]
        ]
        
    }
    
        preferences {
        section {
            input(type: "string", name: "apiKey", title: "<font color='FF0000'><b>OpenAI API Key</b></font>", required: true)
            input(type: "enum", name: "modelPreference", title: "<b>Standard Model</b>", required: true, multiple: false, options: ["gpt-3.5-turbo", "gpt-4"], defaultValue: "gpt-4")
            input(type: "number", name: "callTokens", title: "<b>Model Max Tokens</b>", required: true,  defaultValue: "256")
            input(type: "number", name: "callTemperature", title: "<b>Model Temperature</b>", required: true,  defaultValue: "1")
            input(type: "number", name: "callTopP", title: "<b>Model Top P</b>", required: true,  defaultValue: "1")
            input(type: "number", name: "requestTimeout", title: "<b>Request timeout in seconds</b>", required: true,  defaultValue: "30")
            input(type: "bool", name: "nullNonDetail", title: "<b>Clear response while pending?</b>", required: true,  defaultValue: true)
            input(type: "string", name: "trueFalseSystemContent", title: "<b>Default content for True/False with Detail</b>", required: true,  defaultValue: "You are a home automation system. You will be asked a question, provided detail, etc. You should always respond with [true], [false], or [unknown]. If you want to add extra detail or commentary, add it at the end.")
        }
    }
}

def installed() {
    log.info "Installed"
}

def updated() {
    log.info "Updated"
}

/**
 * Commands
 */

def ask(String system, String question) {
    log.debug("System content: ${system}")
    log.debug("User question: ${question}")
    
    def bodyContent = [
            model: modelPreference,
            messages: [ [ role: "system", content: system], [  role: "user", content: question] ],
            temperature: callTemperature.toBigDecimal(),
            max_tokens: callTokens.toInteger(),
            top_p: callTopP.toBigDecimal()
    ]
    
     makeApiCall(bodyContent, { parseSimpleResponse(it) })
}

def askTrueFalseWithDetail(String question) {
    log.debug("User question for True/False: ${question}")
    
    def bodyContent = [
            model: modelPreference,
            messages: [ 
                [ role: "system", content: trueFalseSystemContent.toString()],
                [ role: "user", content: question] 
            ],
            temperature: callTemperature.toBigDecimal(),
            max_tokens: callTokens.toInteger(),
            top_p: callTopP.toBigDecimal()
    ]
    
     makeApiCall(bodyContent, { parseMultiResponse(it) })
}

/**
 * Calls
 */

private makeApiCall(bodyContent, Closure responseHandler) {
    def apiEndpoint = "https://api.openai.com/v1/chat/completions"
    def bodyContentJson = JsonOutput.toJson(bodyContent)
    log.debug("JSON Body: ${bodyContentJson}")
    
    sendEvent(name: "pendingResponse", value: true)
    
    if (nullNonDetail) {
        sendEvent(name: "responseAnswer", value: null)
        sendEvent(name: "responseValue", value: null)
    }
    
    httpPost(
        uri: apiEndpoint,
        headers: [
            'Authorization': "Bearer ${apiKey}",
            'Content-Type': "application/json"
        ],
        body: bodyContentJson,
        contentType: "application/json",
        timeout: requestTimeout.toInteger()
    ) { response ->
        if (response.status != 200) {
            log.error("Received error response: ${response.getData()}")
        } else {
            responseHandler(response.data)
        }
    }
}

/**
 * Parsing
 */

private parseMultiResponse(response) {
    log.debug("Response: ${response}")
    sendEvent(name: "pendingResponse", value: false)
    if (response && response.choices && response.choices[0] && response.choices[0].message && response.choices[0].message.content) {
        def content = response.choices[0].message.content
        
        // Extract the boolean or unknown value
        def responseValue = (content =~ /^\[(true|false|unknown)\]/)[0][1]
        
        // Check for the ']' delimiter and extract the written answer
        // Considering other options here about consistent parsing, and guidance from the agent.
        def responseAnswer = null
        if (content.contains("]")) {
            responseAnswer = content.split("]")[1].trim()
        }

        if (responseValue) {
            sendEvent(name: "responseValue", value: responseValue)
            if (responseAnswer) {
                sendEvent(name: "responseAnswer", value: responseAnswer)
            }
        } else {
            log.warn "Invalid response received from OpenAI API"
        }
    } else {
        log.warn "Invalid response received from OpenAI API"
    }
}

private parseSimpleResponse(response) {
    log.debug(response)
    if (response && response.choices && response.choices[0] && response.choices[0].message && response.choices[0].message.content) {
        def answer = response.choices[0].message.content.trim()
        sendEvent(name: "responseAnswer", value: answer)
        sendEvent(name: "responseValue", value: null)
    } else {
        log.warn "Invalid response received from OpenAI API"
    }
}
