import { readFileSync, existsSync } from 'node:fs'
const cfg = JSON.parse(readFileSync('src/pages.json', 'utf8'))
const contract = [
  ['首页','pages/index/index'],['登录','pages/user/login'],['我的','pages/user/index'],['资料','pages/user/profile'],
  ['门店列表','pages/store/list'],['门店详情','pages/store/detail'],['商品列表','pages/product/list'],['商品详情','pages/product/detail'],
  ['确认订单','pages/order/confirm'],['订单列表','pages/order/list'],['订单详情','pages/order/detail'],
  ['会员中心','pages/member/index'],['充值记录','pages/member/recharge-records'],['消费记录','pages/member/transactions'],
  ['充值套餐','pages/recharge/index'],['充值结果','pages/recharge/result'],
  ['积分(预留)','pages/member/points'],['优惠券(预留)','pages/member/coupons'],
]
const declared = cfg.pages.map(p => p.path)
console.log('pages.json 声明页面数: ' + declared.length)
let ok = 0
for (const [name, path] of contract) {
  const inCfg = declared.includes(path)
  const file = 'src/' + path + '.vue'
  const hasFile = existsSync(file)
  if (inCfg && hasFile) ok++
  console.log((inCfg && hasFile ? '[OK]  ' : '[MISS]') + ' ' + name.padEnd(12) + path + '  json=' + inCfg + ' vue=' + hasFile)
}
console.log('\n契约第六节 18 页齐全: ' + ok + '/18')
const extra = declared.filter(p => !contract.some(([, c]) => c === p))
console.log('额外页面(购物车，契约外但购物车模块必需): ' + JSON.stringify(extra))
console.log('tabBar: ' + JSON.stringify(cfg.tabBar.list.map(i => i.text + '->' + i.pagePath)))
