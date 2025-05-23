package run.mone.hive.roles;

import lombok.extern.slf4j.Slf4j;
import run.mone.hive.actions.Action;
import run.mone.hive.schema.ActionContext;
import run.mone.hive.schema.ActionReq;
import run.mone.hive.schema.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

/**
 * 取义于Transformer中的Encoder
 * 
 * 盖think方法，根据输入调用一个http接口A(意图识别的接口)，
 * 然后覆盖observe方法获取到意图后根据意图分为：
 *  1.直接调用LLM生成回答， 2. 查询知识库获取答案， 3. 从知识库获取内容做RAG，
 *  这个三个分别对应3个Action，在act方法中处理在observe中选择的action并执行
 * 
 *  TODO: 根据实际需要修改各个Action中的实现逻辑，特别是知识库查询和RAG部分的具体实现
 */
@Slf4j
public class EncodeContentRole extends Role {
    
    private static final String DIRECT_ANSWER = "DIRECT_ANSWER";
    private static final String KNOWLEDGE_QUERY = "KNOWLEDGE_QUERY";
    private static final String RAG = "RAG";
    
    private String intentApiUrl; // 意图识别API的URL
    private String intentType; // 存储当前识别的意图类型
    
    // 三种不同的Action实例
    private DirectAnswerAction directAnswerAction;
    private KnowledgeQueryAction knowledgeQueryAction;
    private RagAction ragAction;
    
    public EncodeContentRole(String name, String profile, String goal, String constraints) {
        super(name, profile, goal, constraints);
        initActions();
    }
    
    public EncodeContentRole(String name, String profile) {
        super(name, profile);
        initActions();
    }
    
    public EncodeContentRole(String name) {
        super(name);
        initActions();
    }
    
    public void setIntentApiUrl(String intentApiUrl) {
        this.intentApiUrl = intentApiUrl;
    }
    
    private void initActions() {
        directAnswerAction = new DirectAnswerAction();
        knowledgeQueryAction = new KnowledgeQueryAction();
        ragAction = new RagAction();
        
        List<Action> actionList = new ArrayList<>();
        actionList.add(directAnswerAction);
        actionList.add(knowledgeQueryAction);
        actionList.add(ragAction);
        
        this.setActions(actionList);
    }
    
    @Override
    protected int observe() {
        log.info("EncodeContentRole observe");
        // 获取最新消息
        if (this.rc.news.isEmpty()) {
            return 0;
        }
        
        // 处理队列中的消息
        this.rc.news.forEach(msg -> this.rc.getMemory().add(msg));
        this.rc.news.clear();
        
        // 获取最新的用户消息
        Message lastMessage = this.rc.getMemory().getLastMessage();
        if (lastMessage == null) {
            return 0;
        }
        
        // 调用意图识别API
        try {
            intentType = identifyIntent(lastMessage.getContent());
            log.info("识别到的意图类型: {}", intentType);
            return 1; // 表示有消息要处理
        } catch (Exception e) {
            log.error("调用意图识别API失败", e);
            // 默认使用直接回答
            intentType = DIRECT_ANSWER;
            return 1;
        }
    }
    
    @Override
    protected int think() {
        log.info("EncodeContentRole think");
        
        // 基于意图类型选择对应的Action
        if (intentType == null) {
            return -1; // 没有识别到意图
        }
        
        // 根据意图类型选择对应的Action
        switch (intentType) {
            case DIRECT_ANSWER:
                this.rc.setTodo(directAnswerAction);
                break;
            case KNOWLEDGE_QUERY:
                this.rc.setTodo(knowledgeQueryAction);
                break;
            case RAG:
                this.rc.setTodo(ragAction);
                break;
            default:
                // 默认使用直接回答
                this.rc.setTodo(directAnswerAction);
                break;
        }
        
        return 1; // 返回1表示有任务要执行
    }
    
    /**
     * 调用意图识别API
     * @param content 用户输入内容
     * @return 意图类型
     */
    private String identifyIntent(String content) throws Exception {
        if (intentApiUrl == null || intentApiUrl.isEmpty()) {
            log.warn("意图识别API URL未设置，默认使用直接回答");
            return DIRECT_ANSWER;
        }
        
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
            
        // 构建请求体
        String requestBody = String.format("{\"content\":\"%s\"}", content);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(intentApiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
            
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // 解析响应，这里假设响应是JSON格式，包含intent字段
        // 实际使用时，需要根据API的返回格式进行解析
        String responseBody = response.body();
        
        // 简单解析示例，实际应该使用JSON库解析
        if (responseBody.contains("\"intent\":\"knowledge_query\"")) {
            return KNOWLEDGE_QUERY;
        } else if (responseBody.contains("\"intent\":\"rag\"")) {
            return RAG;
        } else {
            return DIRECT_ANSWER;
        }
    }
    
    /**
     * 直接使用LLM回答的Action
     */
    private class DirectAnswerAction extends Action {
        
        public DirectAnswerAction() {
            super("DirectAnswerAction", "使用LLM直接生成回答");
        }
        
        @Override
        public CompletableFuture<Message> execute(ActionReq req, ActionContext context) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("执行DirectAnswerAction");
                Message userMessage = req.getMessage();
                
                // 使用LLM直接生成回答
                String answer = llm.chat(userMessage.getContent());
                
                // 创建回复消息
                Message response = new Message();
                response.setContent(answer);
                response.setRole(getName());
                response.setCauseBy(this.getClass().getName());
                response.setCreateTime(System.currentTimeMillis());
                
                return response;
            });
        }
    }
    
    /**
     * 从知识库查询答案的Action
     */
    private class KnowledgeQueryAction extends Action {
        
        public KnowledgeQueryAction() {
            super("KnowledgeQueryAction", "从知识库查询答案");
        }
        
        @Override
        public CompletableFuture<Message> execute(ActionReq req, ActionContext context) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("执行KnowledgeQueryAction");
                Message userMessage = req.getMessage();
                
                // 实现从知识库查询的逻辑
                String query = userMessage.getContent();
                String answer = queryKnowledgeBase(query);
                
                // 创建回复消息
                Message response = new Message();
                response.setContent(answer);
                response.setRole(getName());
                response.setCauseBy(this.getClass().getName());
                response.setCreateTime(System.currentTimeMillis());
                
                return response;
            });
        }
        
        private String queryKnowledgeBase(String query) {
            // 实现知识库查询逻辑
            // 这里只是一个示例，实际应该调用知识库API或服务
            return "这是从知识库中查询到的答案: " + query;
        }
    }
    
    /**
     * 基于知识库内容进行RAG的Action
     */
    private class RagAction extends Action {
        
        public RagAction() {
            super("RagAction", "从知识库获取内容进行RAG");
        }
        
        @Override
        public CompletableFuture<Message> execute(ActionReq req, ActionContext context) {
            return CompletableFuture.supplyAsync(() -> {
                log.info("执行RagAction");
                Message userMessage = req.getMessage();
                
                // 实现RAG逻辑
                String query = userMessage.getContent();
                List<String> relevantDocs = retrieveRelevantDocs(query);
                String answer = generateAnswerWithRAG(query, relevantDocs);
                
                // 创建回复消息
                Message response = new Message();
                response.setContent(answer);
                response.setRole(getName());
                response.setCauseBy(this.getClass().getName());
                response.setCreateTime(System.currentTimeMillis());
                
                return response;
            });
        }
        
        private List<String> retrieveRelevantDocs(String query) {
            // 实现从知识库检索相关文档的逻辑
            // 这里只是一个示例，实际应该调用向量存储库或搜索引擎
            return Arrays.asList(
                "相关文档1的内容...",
                "相关文档2的内容...",
                "相关文档3的内容..."
            );
        }
        
        private String generateAnswerWithRAG(String query, List<String> docs) {
            // 将检索到的文档与查询一起传递给LLM
            StringBuilder context = new StringBuilder();
            context.append("基于以下信息回答问题:\n\n");
            
            for (String doc : docs) {
                context.append("- ").append(doc).append("\n");
            }
            
            context.append("\n问题: ").append(query);
            
            // 调用LLM生成答案
            return llm.chat(context.toString());
        }
    }
}