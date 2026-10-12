<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { api } from '../api/apiClient'
import type { ProductDetail, ProductStatus } from '../types/api'
import { centsFromYuan, moneyFromCents, moneyToYuan, productStatusLabel, readableError } from '../utils/adminFormat'

interface ProductForm {
  productId: string
  name: string
  description: string
  imageUrl: string
  originalPriceYuan: string
  totalStock: number
  status: ProductStatus
}

const emptyForm = (): ProductForm => ({ productId: '', name: '', description: '', imageUrl: '', originalPriceYuan: '', totalStock: 1, status: 'ON_SHELF' })
const queryId = ref('')
const product = ref<ProductDetail | null>(null)
const form = reactive<ProductForm>(emptyForm())
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()
const hasLoadedRecord = ref(false)

const rules: FormRules<ProductForm> = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }, { max: 128, message: '商品名称不能超过 128 个字符', trigger: 'blur' }],
  originalPriceYuan: [{ required: true, message: '请输入商品原价', trigger: 'blur' }, { validator: (_rule, value: string, callback) => {
    try { centsFromYuan(value); callback() } catch (error) { callback(error instanceof Error ? error : new Error('金额格式不正确')) }
  }, trigger: 'blur' }],
  totalStock: [{ required: true, message: '请输入总库存', trigger: 'change' }, { type: 'number', min: 1, message: '总库存至少为 1', trigger: 'change' }],
  status: [{ required: true, message: '请选择商品状态', trigger: 'change' }],
}

function assignProduct(item: ProductDetail) {
  product.value = item
  form.productId = String(item.productId)
  form.name = item.name
  form.description = item.description ?? ''
  form.imageUrl = item.imageUrl ?? ''
  form.originalPriceYuan = moneyToYuan(item.originalPrice)
  form.totalStock = item.totalStock
  form.status = item.status
  hasLoadedRecord.value = true
}

async function loadProduct() {
  const id = queryId.value.trim()
  if (!/^[1-9]\d*$/.test(id)) {
    ElMessage.warning('请输入有效的正整数商品编号。')
    return
  }
  loading.value = true
  try {
    assignProduct(await api.getAdminProduct(id))
    ElMessage.success('商品信息已载入。')
  } catch (error) {
    product.value = null
    hasLoadedRecord.value = false
    ElMessage.error(readableError(error))
  } finally { loading.value = false }
}

function startNewProduct() {
  Object.assign(form, emptyForm())
  product.value = null
  hasLoadedRecord.value = false
  queryId.value = ''
  formRef.value?.clearValidate()
}

async function chooseImage(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件。'); return }
  if (file.size > 5 * 1024 * 1024) { ElMessage.warning('图片不能超过 5 MB。'); return }
  uploading.value = true
  try {
    const result = await api.uploadImage(file)
    form.imageUrl = result.url
    ElMessage.success('图片上传成功。')
  } catch (error) { ElMessage.error(readableError(error)) }
  finally { uploading.value = false }
}

async function saveProduct() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    const originalPrice = centsFromYuan(form.originalPriceYuan)
    const payload = {
      ...(form.productId ? { productId: form.productId } : {}),
      name: form.name.trim(),
      description: form.description.trim(),
      imageUrl: form.imageUrl.trim(),
      originalPrice,
      totalStock: form.totalStock,
      status: form.status,
    }
    const result = await api.saveProduct(payload)
    form.productId = String(result.productId)
    queryId.value = form.productId
    ElMessage.success(`商品已保存，编号 ${form.productId}。`)
    await loadProduct()
  } catch (error) { ElMessage.error(readableError(error)) }
  finally { saving.value = false }
}

async function confirmSave() {
  const message = form.productId ? `确认保存商品 ${form.productId} 的修改吗？` : '确认创建此商品吗？'
  try { await ElMessageBox.confirm(message, '确认保存', { type: 'info', confirmButtonText: '确认保存', cancelButtonText: '再检查一下' }); await saveProduct() }
  catch { /* 用户取消 */ }
}

function statusType(status: ProductStatus) { return status === 'ON_SHELF' ? 'success' : 'info' }
</script>

<template>
  <div class="page-view">
    <section class="page-heading">
      <div><div class="eyebrow">商品与库存</div><h1>商品管理</h1><p>按商品编号载入并维护商品信息，支持商品主图上传。</p></div>
      <el-button @click="startNewProduct">新建商品</el-button>
    </section>

    <el-alert class="content-alert" type="info" :closable="false" show-icon>
      <template #title>当前后端未提供商品列表接口，本页通过商品编号查询和保存，不展示虚构的商品清单。</template>
    </el-alert>

    <section class="panel lookup-panel">
      <div class="panel-heading"><div><h2>查找商品</h2><p>输入已有商品编号以载入并编辑。</p></div></div>
      <div class="lookup-row">
        <el-input v-model="queryId" class="lookup-input" placeholder="请输入商品编号" clearable @keyup.enter="loadProduct" />
        <el-button type="primary" :loading="loading" @click="loadProduct">查询商品</el-button>
        <el-button @click="startNewProduct">创建新商品</el-button>
      </div>
    </section>

    <section class="panel product-editor">
      <div class="panel-heading editor-heading">
        <div><h2>{{ form.productId ? `编辑商品 · ${form.productId}` : '商品信息' }}</h2><p>{{ hasLoadedRecord ? '修改后保存将更新该商品。' : '填写商品信息并保存，商品编号由后端生成。' }}</p></div>
        <el-tag v-if="product" :type="statusType(product.status)" effect="light" round>{{ productStatusLabel[product.status] }}</el-tag>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="product-form" @submit.prevent="confirmSave">
        <el-form-item label="商品名称" prop="name"><el-input v-model="form.name" maxlength="128" show-word-limit placeholder="请输入商品名称" /></el-form-item>
        <el-form-item label="商品编号"><el-input v-model="form.productId" :disabled="hasLoadedRecord" placeholder="新建时由系统生成" /></el-form-item>
        <el-form-item label="商品原价（元）" prop="originalPriceYuan"><el-input v-model="form.originalPriceYuan" inputmode="decimal" placeholder="例如 199.00"><template #prepend>¥</template></el-input><div class="field-hint">接口按分保存，例如 199.00 元将转换为 19900 分。</div></el-form-item>
        <el-form-item label="商品总库存" prop="totalStock"><el-input-number v-model="form.totalStock" :min="1" :max="100000000" controls-position="right" /></el-form-item>
        <el-form-item label="商品状态" prop="status"><el-select v-model="form.status"><el-option label="上架" value="ON_SHELF" /><el-option label="下架" value="OFF_SHELF" /></el-select></el-form-item>
        <el-form-item label="商品描述" class="form-span-2"><el-input v-model="form.description" type="textarea" :rows="4" maxlength="512" show-word-limit placeholder="补充商品说明（可选）" /></el-form-item>
        <el-form-item label="商品主图" class="form-span-2">
          <div class="image-field">
            <div class="image-preview"><img v-if="form.imageUrl" :src="form.imageUrl" alt="商品主图预览" /><span v-else class="image-placeholder">图片预览</span></div>
            <div class="image-actions"><el-input v-model="form.imageUrl" placeholder="图片地址，也可从本地上传" /><input ref="fileInput" type="file" accept="image/png,image/jpeg,image/gif,image/webp,image/bmp" hidden @change="chooseImage" /><el-button :loading="uploading" @click="fileInput?.click()">上传图片</el-button><small>支持常见图片格式，最大 5 MB。</small></div>
          </div>
        </el-form-item>
      </el-form>
      <div class="form-actions"><el-button @click="startNewProduct">重置</el-button><el-button type="primary" :loading="saving" @click="confirmSave">保存商品</el-button></div>
    </section>

    <section v-if="product" class="panel product-facts">
      <div class="panel-heading"><div><h2>库存与缓存信息</h2><p>该信息由商品详情接口返回。</p></div><el-tag effect="plain">{{ product.cacheHit ? '缓存命中' : '数据库查询' }}</el-tag></div>
      <div class="fact-grid"><div><span>可用库存</span><strong>{{ product.availableStock }}</strong></div><div><span>商品总库存</span><strong>{{ product.totalStock }}</strong></div><div><span>当前原价</span><strong>{{ moneyFromCents(product.originalPrice) }}</strong></div></div>
    </section>
  </div>
</template>
