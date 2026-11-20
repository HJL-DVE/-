<template>
  <div ref="el" :style="{ width: '100%', height }" />
</template>

<script setup lang="ts">
/**
 * ECharts 封装：props.option 变化时自动 setOption，容器尺寸变化自适应。
 * 所有报表页面共用，避免每个页面重复初始化/销毁逻辑。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = withDefaults(
  defineProps<{
    option: Record<string, unknown>
    height?: string
  }>(),
  { height: '320px' },
)

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null
let ro: ResizeObserver | null = null

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption(props.option, true)
}

onMounted(() => {
  render()
  ro = new ResizeObserver(() => chart?.resize())
  if (el.value) ro.observe(el.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  ro?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
div { min-height: 80px; }
</style>
