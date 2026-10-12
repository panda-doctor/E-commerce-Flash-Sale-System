<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string | null
  alt: string
  variant?: 'catalog' | 'detail' | 'order'
  loading?: 'eager' | 'lazy'
}>(), { variant: 'catalog', loading: 'lazy' })

const loaded = ref(false)
const failed = ref(false)
watch(() => props.src, () => { loaded.value = false; failed.value = false })
</script>

<template>
  <div class="product-image" :class="`product-image-${variant}`" :role="src && !failed ? undefined : 'img'" :aria-label="src && !failed ? undefined : `${alt}：暂无商品图`">
    <img v-if="src && !failed" :src="src" :alt="alt" :loading="loading" :class="{ 'is-loaded': loaded }" @load="loaded = true" @error="failed = true">
    <span v-if="src && !failed && !loaded" class="product-image-skeleton" aria-hidden="true"></span>
    <span v-if="!src || failed" class="product-image-placeholder"><i aria-hidden="true"></i><small>暂无商品图</small></span>
  </div>
</template>

<style scoped>
.product-image { position: relative; display: grid; width: 100%; height: 100%; overflow: hidden; place-items: center; background: #f4f6f8; }
.product-image img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0; transition: opacity .18s ease; }
.product-image img.is-loaded { opacity: 1; }
.product-image-skeleton { position: absolute; inset: 0; background: linear-gradient(100deg, #f0f2f5 25%, #f8f9fb 42%, #f0f2f5 60%); background-size: 220% 100%; animation: image-wave 1.15s ease-in-out infinite; }
.product-image-placeholder { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; flex-direction: column; gap: 10px; color: #9aa4b1; background: #f5f6f8; }
.product-image-placeholder i { position: relative; width: 38px; height: 31px; border: 1px solid #c8ced6; border-radius: 5px; }
.product-image-placeholder i::before { position: absolute; right: 4px; bottom: 4px; left: 4px; height: 12px; border-bottom: 1px solid #c8ced6; background: linear-gradient(140deg, transparent 48%, #c8ced6 49%, #c8ced6 52%, transparent 53%); content: ""; }
.product-image-placeholder i::after { position: absolute; top: 4px; right: 5px; width: 5px; height: 5px; border: 1px solid #c8ced6; border-radius: 50%; content: ""; }
.product-image-placeholder small { font-size: 10px; }
.product-image-detail .product-image-placeholder i { transform: scale(1.5); margin-bottom: 8px; }
.product-image-order { border-radius: inherit; }
.product-image-order .product-image-placeholder { gap: 4px; padding: 4px; text-align: center; }
.product-image-order .product-image-placeholder i { width: 23px; height: 19px; }
.product-image-order .product-image-placeholder small { font-size: 8px; }
@keyframes image-wave { to { background-position: -220% 0; } }
@media (prefers-reduced-motion: reduce) { .product-image img { transition: none; }.product-image-skeleton { animation: none; } }
</style>
