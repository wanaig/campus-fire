// 后端接口地址：
// - 微信开发者工具模拟器可直接使用 http://127.0.0.1:8080/api
// - 真机调试使用电脑的局域网地址（当前 WLAN IP），手机与电脑需连同一 Wi-Fi
// - 手机连不上时先确认：1) 电脑 WLAN IP 是否变化 2) Windows 防火墙已放行 8080
// - 说明：默认用 IP 而不是 localhost，可避免部分代理软件（Clash TUN 模式）劫持 localhost
const BASE_URL = 'http://192.168.0.187:8080/api'

module.exports = { BASE_URL }
