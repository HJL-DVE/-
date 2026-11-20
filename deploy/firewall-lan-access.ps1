#requires -RunAsAdministrator
# TeaClub 局域网访问放行（右键 → 使用管理员身份运行，只需执行一次）
# 放行：8080(Web后台) 9000(Gateway) 8848(Nacos) 15672(RabbitMQ管理台)

$ports = @(
  @{Port=8080;  Name="TeaClub Web 管理后台"},
  @{Port=9000;  Name="TeaClub Gateway API"},
  @{Port=8848;  Name="TeaClub Nacos 控制台"},
  @{Port=15672; Name="TeaClub RabbitMQ 管理台"}
)

foreach ($p in $ports) {
  $existing = Get-NetFirewallRule -DisplayName $p.Name -ErrorAction SilentlyContinue
  if ($existing) {
    Write-Host ("[跳过] 已存在: " + $p.Name)
  } else {
    New-NetFirewallRule -DisplayName $p.Name -Direction Inbound -Protocol TCP -LocalPort $p.Port -Action Allow -Profile Any | Out-Null
    Write-Host ("[放行] " + $p.Name + " : 端口 " + $p.Port)
  }
}

Write-Host ""
Write-Host "完成。局域网其他设备访问地址："
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like "192.168.*" -and $_.InterfaceAlias -notmatch "VMware|VirtualBox|vEthernet" } | ForEach-Object {
  Write-Host ("    http://" + $_.IPAddress + ":8080   (Web 管理后台)")
  Write-Host ("    http://" + $_.IPAddress + ":9000   (Gateway API)")
}

Read-Host "按回车关闭"