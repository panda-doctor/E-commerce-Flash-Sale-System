<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { api, type AiChatTurn } from '../api/apiClient'
import { ApiError } from '../types/api'

interface ChatMessage extends AiChatTurn { id: number; createdAt: number; failed?: boolean }

const open = ref(false)
const draft = ref('')
const sending = ref(false)
const error = ref('')
const messages = ref<ChatMessage[]>([
  { id: 0, role: 'assistant', content: '你好，我是闪购集市智能客服。可以咨询商品、秒杀规则和活动信息；具体订单进度请在“我的订单”中查询。', createdAt: Date.now() },
])
const conversation = ref<AiChatTurn[]>([])
const messageList = ref<HTMLElement>()
const messageInput = ref<HTMLTextAreaElement>()
const canSend = computed(() => draft.value.trim().length > 0 && !sending.value)
let messageId = 1

const quickQuestions = ['如何参加秒杀？', '活动什么时候开始？', '如何查看我的订单？']

watch(messages, async () => {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}, { deep: true })

watch(open, async (isOpen) => {
  if (!isOpen) return
  await nextTick()
  messageInput.value?.focus()
})

async function send(text = draft.value) {
  const question = text.trim()
  if (!question || sending.value) return
  if (question.length > 500) {
    error.value = '每条消息最多输入 500 个字。'
    return
  }
  error.value = ''
  draft.value = ''
  const userMessage: ChatMessage = { id: messageId++, role: 'user', content: question, createdAt: Date.now() }
  messages.value.push(userMessage)
  sending.value = true
  try {
    const result = await api.aiChat({ message: question, history: conversation.value.slice(-10) })
    const answer = result.reply.trim()
    messages.value.push({ id: messageId++, role: 'assistant', content: answer || '暂时没有收到有效回复，请稍后重试。', createdAt: Date.now() })
    conversation.value.push({ role: 'user', content: question }, { role: 'assistant', content: answer })
    conversation.value = conversation.value.slice(-10)
  } catch (cause) {
    const detail = cause instanceof ApiError ? cause.message : cause instanceof Error ? cause.message : '客服暂时无法响应。'
    error.value = `${detail} 你的问题已保留，可重试发送。`
    userMessage.failed = true
  } finally {
    sending.value = false
  }
}

async function retry(message: ChatMessage) {
  if (sending.value || !message.failed) return
  message.failed = false
  sending.value = true
  error.value = ''
  try {
    const result = await api.aiChat({ message: message.content, history: conversation.value.slice(-10) })
    const answer = result.reply.trim()
    messages.value.push({ id: messageId++, role: 'assistant', content: answer || '暂时没有收到有效回复，请稍后重试。', createdAt: Date.now() })
    conversation.value.push({ role: 'user', content: message.content }, { role: 'assistant', content: answer })
    conversation.value = conversation.value.slice(-10)
  } catch (cause) {
    message.failed = true
    error.value = cause instanceof Error ? `${cause.message} 请检查服务后重试。` : '客服暂时无法响应，请稍后重试。'
  } finally {
    sending.value = false
  }
}

function startNewConversation() {
  conversation.value = []
  messages.value = [{ id: messageId++, role: 'assistant', content: '已开启新会话。请问有什么可以帮你？', createdAt: Date.now() }]
  error.value = ''
}

function formatTime(value: number): string {
  return new Date(value).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
}
</script>

<template>
  <div class="chat-widget" :class="{ 'chat-widget-open': open }">
    <section v-if="open" class="chat-panel" aria-label="智能客服">
      <header class="chat-header">
        <div class="chat-avatar" aria-hidden="true">闪</div>
        <div class="chat-heading"><strong>智能客服</strong><span><i></i>在线答疑</span></div>
        <button class="chat-new" type="button" aria-label="开启新会话" title="开启新会话" @click="startNewConversation">＋</button>
        <button class="chat-close" type="button" aria-label="关闭客服" @click="open = false">×</button>
      </header>
      <div ref="messageList" class="chat-messages" aria-live="polite" aria-relevant="additions text">
        <article v-for="message in messages" :key="message.id" class="chat-message" :class="`chat-${message.role}`">
          <span class="chat-message-avatar" aria-hidden="true">{{ message.role === 'assistant' ? '闪' : '我' }}</span>
          <div class="chat-message-body"><p>{{ message.content }}</p><button v-if="message.failed" class="chat-retry" type="button" @click="retry(message)">重试发送</button><time>{{ formatTime(message.createdAt) }}</time></div>
        </article>
        <div v-if="sending" class="chat-message chat-assistant"><span class="chat-message-avatar">闪</span><div class="chat-typing" role="status">正在整理回复 <i></i><i></i><i></i></div></div>
      </div>
      <div v-if="messages.length <= 1" class="chat-shortcuts" aria-label="常见问题">
        <button v-for="question in quickQuestions" :key="question" type="button" :disabled="sending" @click="send(question)">{{ question }}</button>
      </div>
      <p v-if="error" class="chat-error" role="alert">{{ error }}</p>
      <form class="chat-composer" @submit.prevent="send()">
        <label class="sr-only" for="chat-message">输入客服问题</label>
        <textarea id="chat-message" ref="messageInput" v-model="draft" rows="1" maxlength="500" placeholder="输入你的问题…" :disabled="sending" @keydown.enter.exact.prevent="send()" @keydown.enter.shift.stop />
        <div class="chat-compose-foot"><span>{{ draft.length }}/500 · 回车发送，Shift + 回车换行</span><button type="submit" :disabled="!canSend" :aria-label="sending ? '正在发送消息' : '发送消息'">{{ sending ? '发送中…' : '发送' }}</button></div>
      </form>
    </section>
    <button class="chat-launcher" type="button" :aria-expanded="open" aria-label="打开智能客服" @click="open = !open">
      <svg v-if="!open" viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.75A2.75 2.75 0 0 1 6.75 3h10.5A2.75 2.75 0 0 1 20 5.75v7.5A2.75 2.75 0 0 1 17.25 16H11l-4.8 4v-4.14A2.75 2.75 0 0 1 4 13.25z" /></svg>
      <span v-else aria-hidden="true">×</span><span class="chat-launcher-label">{{ open ? '收起客服' : '智能客服' }}</span>
    </button>
  </div>
</template>

<style scoped>
.chat-widget { position: fixed; z-index: 50; right: 28px; bottom: 26px; display: flex; align-items: flex-end; flex-direction: column; gap: 12px; font-family: inherit; }
.chat-panel { display: flex; width: 370px; height: min(570px, calc(100dvh - 118px)); min-height: 410px; flex-direction: column; overflow: hidden; border: 1px solid #e4e9f1; border-radius: 18px; background: #fff; box-shadow: 0 22px 70px #1b355326; }
.chat-header { display: flex; height: 70px; flex: none; align-items: center; gap: 11px; padding: 0 16px; color: white; background: var(--store-primary, #f43f3f); }
.chat-avatar { display: grid; width: 38px; height: 38px; place-items: center; border: 1px solid #ffffff55; border-radius: 13px; background: #ffffff21; font-weight: 800; }
.chat-heading { display: flex; flex: 1; flex-direction: column; gap: 4px; }.chat-heading strong { font-size: 14px; }.chat-heading span { display: flex; align-items: center; gap: 5px; color: #e5f0ff; font-size: 10px; }.chat-heading i { width: 6px; height: 6px; border-radius: 50%; background: #7ee5b4; }
.chat-new,.chat-close { display: grid; width: 30px; height: 30px; place-items: center; border: 1px solid #ffffff4a; border-radius: 9px; color: white; background: #ffffff14; font-size: 19px; cursor: pointer; }.chat-new:hover,.chat-close:hover { background: #ffffff30; }
.chat-messages { display: flex; flex: 1; flex-direction: column; gap: 15px; overflow-y: auto; padding: 16px 15px 12px; background: #f7f9fc; }.chat-date { align-self: center; margin: 0 0 2px; color: #a0aabb; font-size: 10px; }
.chat-message { display: flex; max-width: 94%; align-items: flex-start; gap: 8px; }.chat-message-avatar { display: grid; width: 27px; height: 27px; flex: none; place-items: center; border: 1px solid #e5eaf1; border-radius: 9px; color: var(--store-primary, #f43f3f); background: white; font-size: 10px; font-weight: 700; }.chat-user { align-self: flex-end; flex-direction: row-reverse; }.chat-user .chat-message-avatar { color: #fff; border-color: var(--store-primary, #f43f3f); background: var(--store-primary, #f43f3f); }.chat-message-body p,.chat-typing { margin: 0; padding: 10px 12px; border: 1px solid #e7ebf1; border-radius: 4px 13px 13px; color: #3c495e; background: #fff; font-size: 12px; line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }.chat-user .chat-message-body p { border-color: var(--store-primary, #f43f3f); border-radius: 13px 4px 13px 13px; color: white; background: var(--store-primary, #f43f3f); }.chat-message-body time { display: block; margin: 4px 2px 0; color: #a1aabb; font-size: 9px; }.chat-user .chat-message-body time { text-align: right; }.chat-retry { margin-top: 7px; padding: 0; border: 0; color: var(--store-primary, #f43f3f); background: transparent; font-size: 10px; cursor: pointer; }.chat-typing { color: #758398; }.chat-typing i { display: inline-block; width: 4px; height: 4px; margin-left: 3px; border-radius: 50%; background: var(--store-primary, #f43f3f); animation: chat-pulse .8s ease-in-out infinite alternate; }.chat-typing i:nth-child(2) { animation-delay: .2s; }.chat-typing i:nth-child(3) { animation-delay: .4s; }
.chat-shortcuts { display: grid; gap: 6px; padding: 11px 14px 2px; background: #f7f9fc; }.chat-shortcuts button { display: flex; align-items: center; justify-content: space-between; padding: 8px 10px; border: 1px solid #e4eaf3; border-radius: 8px; color: #526782; background: white; font-size: 10px; text-align: left; cursor: pointer; }.chat-shortcuts button:hover { border-color: #f1c6c4; color: var(--store-primary, #f43f3f); }.chat-shortcuts button:disabled { opacity: .6; }
.chat-error { margin: 0; padding: 8px 14px 0; color: #bd4e47; background: #fff; font-size: 10px; line-height: 1.5; }.chat-composer { padding: 11px 13px 12px; border-top: 1px solid #ebeff4; background: #fff; }.chat-composer textarea { display: block; width: 100%; min-height: 42px; max-height: 88px; resize: vertical; border: 0; outline: 0; color: #344258; background: transparent; font: inherit; font-size: 12px; line-height: 1.6; }.chat-composer textarea::placeholder { color: #a5afbd; }.chat-compose-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #a1aaba; font-size: 9px; }.chat-compose-foot button { min-width: 58px; height: 30px; border: 0; border-radius: 8px; color: white; background: var(--store-primary, #f43f3f); font-size: 11px; font-weight: 650; cursor: pointer; }.chat-compose-foot button:disabled { background: #c7d3e2; cursor: not-allowed; }
.chat-launcher { display: flex; min-height: 48px; align-items: center; gap: 9px; padding: 0 17px; border: 0; border-radius: 25px; color: white; background: var(--store-primary, #f43f3f); box-shadow: 0 8px 20px #f43f3f30; font-size: 12px; font-weight: 650; cursor: pointer; transition: transform .18s, background .18s; }.chat-launcher:hover { transform: translateY(-2px); background: var(--store-primary-hover, #e52d2d); }.chat-launcher svg { width: 19px; height: 19px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }.chat-launcher>span:first-child { font-size: 22px; line-height: 1; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; clip-path: inset(50%); border: 0; }
@keyframes chat-pulse { to { opacity: .25; transform: translateY(-2px); } }
@media (max-width: 560px) { .chat-widget { right: 12px; bottom: 76px; gap: 8px; }.chat-panel { width: min(390px, calc(100vw - 24px)); height: min(590px, calc(100dvh - 150px)); min-height: 340px; border-radius: 15px; }.chat-launcher { width: 46px; min-width: 46px; min-height: 46px; justify-content: center; padding: 0; }.chat-launcher-label { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; clip-path: inset(50%); }.chat-compose-foot span { font-size: 8px; } }
@media (prefers-reduced-motion: reduce) { .chat-launcher,.chat-typing i { animation: none; transition: none; } }
.chat-header,
.chat-user .chat-message-avatar,
.chat-user .chat-message-body p,
.chat-compose-foot button:not(:disabled),
.chat-launcher { background-color: var(--store-primary-action, #c32929); }
.chat-user .chat-message-avatar,
.chat-user .chat-message-body p { border-color: var(--store-primary-action, #c32929); }
.chat-launcher:hover { background-color: var(--store-primary-action-hover, #a91f1f); }
.chat-shortcuts button:hover,
.chat-retry { color: var(--store-primary-action-hover, #a91f1f); }
.chat-composer textarea::placeholder,
.chat-compose-foot,
.chat-message-body time { color: #68717d; }
.chat-widget :is(button, textarea):focus-visible { outline: 3px solid #c3292966; outline-offset: 2px; }
@media (max-width: 560px) { .chat-compose-foot button { min-height: 40px; } }
</style>
