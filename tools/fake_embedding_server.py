"""
本地假 OpenAI 兼容服务，仅用于没有 API Key 时验证文档索引和 Agent 工具调用链路。

- POST /v1/embeddings：返回固定维度假向量。
- POST /v1/chat/completions：模拟一个会调用工具的模型。

注意：这里的 if/else 是“假模型”的测试脚本策略，不是生产代码。
生产环境的工具选择完全由真实模型通过 tool_calls 决定。
"""
import itertools
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOOL_CALL_IDS = itertools.count(1)


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.read_body()
        if self.path == "/v1/embeddings":
            self.handle_embeddings(body)
        elif self.path == "/v1/chat/completions":
            self.handle_chat(body)
        else:
            self.send_error(404)

    def handle_embeddings(self, body):
        print("[fake-openai] embeddings request: "
              + body.decode("utf-8", "replace"), flush=True)
        payload = json.loads(body or b"{}")
        inputs = payload.get("input", [])
        if isinstance(inputs, str):
            inputs = [inputs]

        data = []
        for index, text in enumerate(inputs):
            seed = hash(text)
            vector = [((seed >> (i * 3)) & 7) / 10.0 + 0.01 for i in range(8)]
            data.append({
                "object": "embedding",
                "index": index,
                "embedding": vector
            })

        self.send_json({
            "object": "list",
            "data": data,
            "model": payload.get("model", "fake-embedding"),
            "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 0,
                "total_tokens": 1
            }
        })

    def handle_chat(self, body):
        print("[fake-openai] chat request: "
              + body.decode("utf-8", "replace"), flush=True)
        payload = json.loads(body or b"{}")
        messages = payload.get("messages", [])
        model = payload.get("model", "fake-chat")

        user_text = ""
        for message in reversed(messages):
            if message.get("role") == "user":
                user_text = message.get("content") or ""
                break

        tool_messages = [m for m in messages if m.get("role") == "tool"]

        # 测试专用：强制模型一直要求调用工具，用于验证后端最大调用次数
        if "触发工具上限" in user_text:
            self.send_tool_call(model, "searchKnowledgeBase",
                                {"query": "触发上限测试", "topK": 4})
            return

        # 测试专用：发送一次非法参数，验证工具参数校验和错误回传
        if not tool_messages and "触发无效参数" in user_text:
            self.send_tool_call(model, "searchKnowledgeBase",
                                {"query": "", "topK": 99})
            return

        if tool_messages:
            last_tool_result = tool_messages[-1].get("content") or ""
            self.send_final(
                model,
                "模拟模型回答：工具已经执行，下面是工具返回结果的部分内容：\n"
                + last_tool_result[:800]
            )
            return

        lower = user_text.lower()
        if any(keyword in lower for keyword in
               ["日志", "异常", "exception", "error", "报错", "堆栈"]):
            self.send_tool_call(model, "analyzeErrorLog",
                                {"logText": user_text, "maxLines": 10})
            return

        if any(keyword in lower for keyword in ["你好", "hello", "hi"]):
            self.send_final(model, "你好，我是模拟模型。你可以让我检索技术文档或分析错误日志。")
            return

        self.send_tool_call(model, "searchKnowledgeBase",
                            {"query": user_text, "topK": 4})

    def send_tool_call(self, model, tool_name, arguments):
        call_id = "call_%d" % next(TOOL_CALL_IDS)
        self.send_json({
            "id": "chatcmpl-fake",
            "object": "chat.completion",
            "created": 0,
            "model": model,
            "choices": [{
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": None,
                    "tool_calls": [{
                        "id": call_id,
                        "type": "function",
                        "function": {
                            "name": tool_name,
                            "arguments": json.dumps(arguments, ensure_ascii=False)
                        }
                    }]
                },
                "finish_reason": "tool_calls"
            }],
            "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 1,
                "total_tokens": 2
            }
        })

    def send_final(self, model, content):
        self.send_json({
            "id": "chatcmpl-fake",
            "object": "chat.completion",
            "created": 0,
            "model": model,
            "choices": [{
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": content
                },
                "finish_reason": "stop"
            }],
            "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 1,
                "total_tokens": 2
            }
        })

    def send_json(self, value):
        output = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(output)))
        self.end_headers()
        self.wfile.write(output)

    def read_body(self):
        """兼容 Content-Length 和 chunked 两种请求体。"""
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            data = b""
            while True:
                line = self.rfile.readline().strip()
                size = int(line.split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    break
                data += self.rfile.read(size)
                self.rfile.read(2)
            return data

        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length)

    def log_message(self, fmt, *args):
        print("[fake-openai] " + fmt % args, flush=True)


if __name__ == "__main__":
    print("fake OpenAI server: http://127.0.0.1:9999/v1/embeddings and /v1/chat/completions")
    ThreadingHTTPServer(("127.0.0.1", 9999), Handler).serve_forever()
