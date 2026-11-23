<template>
  <div class="page">
    <div class="page-card">
      <div class="toolbar">
        <el-input v-model="query.phone" placeholder="手机号" clearable style="width: 160px" @keyup.enter="search" />
        <el-input v-model="query.keyword" placeholder="昵称/会员号" clearable style="width: 180px" @keyup.enter="search" />
        <el-select v-model="query.levelId" placeholder="会员等级" clearable style="width: 150px">
          <el-option v-for="level in levels" :key="level.id" :label="level.levelName" :value="level.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="正常" value="NORMAL" />
          <el-option label="已冻结" value="FROZEN" />
          <el-option label="已停用" value="DISABLED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
        <el-button v-if="userStore.hasPerm('member:create')" type="success" @click="openCreate">新增会员</el-button>
        <div class="toolbar-spacer" />
        <span class="text-muted">共 {{ total }} 位会员</span>
      </div>

      <el-table :data="records" v-loading="loading" border stripe>
        <el-table-column prop="memberNo" label="会员号" width="150" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="levelName" label="等级" width="110" />
        <el-table-column label="累计消费" width="120" align="right">
          <template #default="{ row }">
            <span class="money">{{ fenToYuan(row.totalConsumeAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="累计充值" width="120" align="right">
          <template #default="{ row }">
            <span class="money-muted">{{ fenToYuan(row.totalRechargeAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="points" label="积分" width="90" align="right" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="tagTypeByStatus(row.status)">
              {{ MEMBER_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push('/member/detail/' + row.id)">详情</el-button>
            <el-button v-if="userStore.hasPerm('member:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('member:status')" link :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'NORMAL' ? '冻结' : '解冻' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" title="编辑会员" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.gender" clearable style="width: 100%">
            <el-option label="男" value="MALE" />
            <el-option label="女" value="FEMALE" />
            <el-option label="未知" value="UNKNOWN" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="form.levelId" clearable style="width: 100%">
            <el-option v-for="level in levels" :key="level.id" :label="level.levelName" :value="level.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增会员（后台手工建档） -->
    <el-dialog v-model="createVisible" title="新增会员" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
        title="后台建档适用于到店办卡 / 纸质会员迁移；手机号在品牌内唯一"
      />
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="手机号" required>
          <el-input v-model="createForm.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="称呼">
          <el-input v-model="createForm.nickname" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.gender" style="width: 100%">
            <el-option label="未知" value="UNKNOWN" />
            <el-option label="男" value="MALE" />
            <el-option label="女" value="FEMALE" />
          </el-select>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="createForm.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="等级">
          <el-select v-model="createForm.levelId" clearable placeholder="默认等级" style="width: 100%">
            <el-option v-for="level in levels" :key="level.id" :label="level.levelName" :value="level.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="注册门店">
          <el-select v-model="createForm.registerStoreId" clearable placeholder="默认门店" style="width: 100%">
            <el-option v-for="store in appStore.stores" :key="store.id" :label="store.storeName" :value="store.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" placeholder="留空默认 TeaClub@123" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { createMember, getMemberLevels, getMemberPage, updateMember, updateMemberStatus } from '@/api/member'
import { MEMBER_STATUS_LABEL, fenToYuan, formatDateTime, tagTypeByStatus } from '@/utils/format'
import type { MemberLevelVO, MemberVO } from '@/api/types'

const loading = ref(false)
const appStore = useAppStore()
const userStore = useUserStore()
const submitting = ref(false)
const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive<{
  phone: string
  nickname: string
  gender: string
  birthday: string
  levelId?: number
  registerStoreId?: number
  password: string
}>({
  phone: '', nickname: '', gender: 'UNKNOWN', birthday: '',
  levelId: undefined, registerStoreId: undefined, password: '',
})

function openCreate(): void {
  Object.assign(createForm, {
    phone: '', nickname: '', gender: 'UNKNOWN', birthday: '',
    levelId: undefined, registerStoreId: appStore.currentStoreId || undefined, password: '',
  })
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!/^1\d{10}$/.test(createForm.phone)) {
    ElMessage.warning('请输入 11 位手机号')
    return
  }
  createSubmitting.value = true
  try {
    await createMember({
      phone: createForm.phone,
      nickname: createForm.nickname || undefined,
      gender: createForm.gender,
      birthday: createForm.birthday || undefined,
      levelId: createForm.levelId,
      registerStoreId: createForm.registerStoreId,
      password: createForm.password || undefined,
    })
    ElMessage.success('会员建档成功')
    createVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error((e as Error)?.message || '建档失败，请检查手机号是否重复')
  } finally {
    createSubmitting.value = false
  }
}
const records = ref<MemberVO[]>([])
const levels = ref<MemberLevelVO[]>([])
const total = ref(0)

const query = reactive<{ pageNo: number; pageSize: number; phone: string; keyword: string; levelId?: number; status: string }>({
  pageNo: 1,
  pageSize: 20,
  phone: '',
  keyword: '',
  levelId: undefined,
  status: '',
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await getMemberPage({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      phone: query.phone || undefined,
      keyword: query.keyword || undefined,
      levelId: query.levelId || undefined,
      status: query.status || undefined,
    })
    records.value = page.records || []
    total.value = page.total || 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  query.pageNo = 1
  load()
}

function reset(): void {
  query.phone = ''
  query.keyword = ''
  query.levelId = undefined
  query.status = ''
  search()
}

const dialogVisible = ref(false)
const editingId = ref(0)
const form = reactive<{ nickname: string; gender: string; levelId?: number; birthday: string; remark: string }>({
  nickname: '',
  gender: '',
  levelId: undefined,
  birthday: '',
  remark: '',
})

function openEdit(row: MemberVO): void {
  editingId.value = row.id
  Object.assign(form, {
    nickname: row.nickname || '',
    gender: row.gender || '',
    levelId: row.levelId || undefined,
    birthday: row.birthday || '',
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  submitting.value = true
  try {
    await updateMember(editingId.value, {
      nickname: form.nickname || undefined,
      gender: form.gender || undefined,
      levelId: form.levelId,
      birthday: form.birthday || undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('会员已更新')
    dialogVisible.value = false
    await load()
  } finally {
    submitting.value = false
  }
}

function toggleStatus(row: MemberVO): void {
  const next = row.status === 'NORMAL' ? 'FROZEN' : 'NORMAL'
  ElMessageBox.confirm(
    '确认将会员「' + (row.nickname || row.phone) + '」' + (next === 'FROZEN' ? '冻结' : '解冻') + '？',
    '提示',
    { type: 'warning' },
  )
    .then(async () => {
      await updateMemberStatus(row.id, next, next === 'FROZEN' ? '管理端冻结' : undefined)
      ElMessage.success('操作成功')
      await load()
    })
    .catch(() => undefined)
}

onMounted(async () => {
  await load()
  try {
    levels.value = await getMemberLevels()
  } catch {
    levels.value = []
  }
})
</script>
