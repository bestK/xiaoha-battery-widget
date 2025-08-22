/**
 * 小哈电池电量提醒小部件
 * 版本：v0.0.3
 * 源码地址：https://github.com/bestK/xiaoha-battery-widget
 */
const batteryNo = '8903115649'; // ← 可改成你的电池编号
const token = '你的token'; // stream 抓包获取的 token
const batteryLifeNotice = [90, 30, 25, 10]; // 通知电量阈值
const widget = new ListWidget();
widget.backgroundColor = new Color('#0088fe');

if (!batteryNo) {
    const errorText = widget.addText('⚠️ 参数缺失');
    errorText.font = Font.systemFont(10);
    errorText.textColor = Color.white();
    errorText.centerAlignText();
    Script.setWidget(widget);
    Script.complete();
    return;
}

// decode
const decode_endpoint = `https://xiaoha.linkof.link/decode`;
// preparams
const preparams_endpoint = `https://xiaoha.linkof.link/preparams?batteryNo=${batteryNo}`;

async function createWidget() {
    try {
        const preparams_req = new Request(preparams_endpoint);
        preparams_req.method = 'POST';
        preparams_req.headers = {
            'Content-Type': 'application/json',
        };
        preparams_req.body = token;
        const preparams_res = await preparams_req.loadJSON();

        // 检查预处理参数响应是否有效
        if (!preparams_res || !preparams_res.data) {
            throw new Error('预处理参数响应数据无效');
        }

        const { url, body, headers } = preparams_res.data;

        const battery_req = new Request(url);

        battery_req.method = 'POST';
        battery_req.headers = headers;
        battery_req.body = body;

        const battery_res = await battery_req.load();

        // call decode endpoint
        const decode_req = new Request(decode_endpoint);
        decode_req.method = 'POST';
        decode_req.headers = {
            'Content-Type': 'application/json',
        };
        decode_req.body = battery_res;
        const decode_res = await decode_req.loadJSON();

        // 检查响应数据是否有效
        if (!decode_res || !decode_res.data) {
            throw new Error('解码响应数据无效');
        }

        const { reportTime, batteryLife } = decode_res.data.data.bindBatteries[0];

        if (!batteryLife || typeof batteryLife === 'undefined' || !reportTime) {
            throw new Error('电池数据不完整');
        }

        const reportDate = new Date(reportTime);
        const formattedTime = `${reportDate.getMonth() + 1}/${reportDate.getDate()} ${reportDate.getHours()}:${String(reportDate.getMinutes()).padStart(2, '0')}`;

        // 获取上次记录的电量
        const lastBatteryKey = `last_battery_${batteryNo}`;
        const lastBatteryLife = Keychain.contains(lastBatteryKey) ? parseInt(Keychain.get(lastBatteryKey)) : null;

        // 检查电量是否低于阈值并找出最小的触发阈值
        const triggeredThresholds = batteryLifeNotice.filter(threshold => batteryLife < threshold);
        if (triggeredThresholds.length > 0) {
            const lowestThreshold = Math.min(...triggeredThresholds);
            // 只在电量发生变化时发送通知
            if (lastBatteryLife !== batteryLife) {
                const notification = new Notification();
                notification.title = '电池电量提醒';
                notification.body = `电池${batteryNo}电量已降至${batteryLife}%，低于${lowestThreshold}%`;
                // 立即发送通知
                notification.schedule();
            }
        }

        // 保存当前电量
        Keychain.set(lastBatteryKey, batteryLife.toString());

        widget.setPadding(0, 0, 0, 0);

        // 上部 spacer
        widget.addSpacer();

        // 水平居中进度圈
        const centerStack = widget.addStack();
        centerStack.addSpacer();

        const progressStack = await progressCircle(
            centerStack,
            batteryLife,
            '#ffffff-#ffffff',
            'rgba(255,255,255,0.3)-rgba(255,255,255,0.3)',
            90,
            8,
        );

        const percentStack = progressStack.addStack();
        percentStack.centerAlignContent();

        const percentText = percentStack.addText(`${batteryLife}%`);
        percentText.font = Font.boldSystemFont(18);
        percentText.textColor = Color.white();
        percentText.centerAlignText();

        centerStack.addSpacer();

        // 中部 spacer
        widget.addSpacer();

        // 底部堆叠
        const bottomStack = widget.addStack();
        bottomStack.layoutHorizontally();
        bottomStack.centerAlignContent();
        bottomStack.setPadding(0, 80, 5, 5);

        // 左下 logo
        const logoStack = bottomStack.addStack();
        const logoBase64 =
            'iVBORw0KGgoAAAANSUhEUgAAABwAAAAcCAMAAABF0y+mAAAAbFBMVEUAiP4Ah/4AhP4lj/4Agv4Af/6Qv//////R4/8Aff5Uov7w9//p8/9lqf/I3v/w9f+pzv9srP/4/P+82P+Huf7e6v9xsf99tf640/8ylP6hyf9Hnf6dxv8Ag/7k7/8Aev7X5/8Adv5AmP4RjP7GGMZdAAAA9klEQVR4AdXLBwKDIAxA0QSDwb23VKX3v2MZnUfodzAewJ+Gn1noy0T0WuL3aZKSgGJWAkFImaRZltsnK4S1sqrqpOG47aToq2po2pGbdsomi7KaS7Xwmmy8J3O18pgB6za6BZx2lXUH2dvbPGxccO6fJuCq0rW2TTgPKUMxTrYCIeB5daXNSI9LxcxtSU9UULsKjxGfy2a6m3xhw8MwtOpweB/NSkdeh5vjbpHk1Z0WN76T4a7nBR0OQ9bZ8zpRXdJzySQSwxwT2NCoLpLtWaxcCKzPlPou41iCD4kQzZDlk7ZzKag794XgKxSA+jkXpBF+Q/jzHpg8EYrSfggvAAAAAElFTkSuQmCC';
        const logoImage = Image.fromData(Data.fromBase64String(logoBase64));
        const logo = logoStack.addImage(logoImage);
        logo.imageSize = new Size(20, 20); // 控制 logo 尺寸
        logoStack.addSpacer(5);

        // 右下角文本
        const infoStack = bottomStack.addStack();
        infoStack.layoutVertically();

        const idText = infoStack.addText(batteryNo);
        idText.font = Font.systemFont(7);
        idText.textColor = Color.white();
        idText.rightAlignText();
        idText.textOpacity = 0.8;

        const timeText = infoStack.addText(formattedTime);
        timeText.font = Font.systemFont(7);
        timeText.textColor = Color.white();
        timeText.rightAlignText();
        timeText.textOpacity = 0.8;

        Script.setWidget(widget);
    } catch (err) {
        console.error(err);
        const errorText = widget.addText('加载失败');
        errorText.font = Font.systemFont(10);
        errorText.textColor = Color.white();
        errorText.centerAlignText();
        Script.setWidget(widget);
    }
}

await createWidget();
Script.complete();

async function progressCircle(on, value = 50, colour = 'white', background = 'gray', size = 56, barWidth = 5.5) {
    if (value > 1) value /= 100;
    if (value < 0) value = 0;
    if (value > 1) value = 1;

    async function isUsingDarkAppearance() {
        return !Color.dynamic(Color.white(), Color.black()).red;
    }

    let isDark = await isUsingDarkAppearance();
    if (colour.includes('-')) colour = isDark ? colour.split('-')[1] : colour.split('-')[0];
    if (background.includes('-')) background = isDark ? background.split('-')[1] : background.split('-')[0];

    let w = new WebView();
    await w.loadHTML('<canvas id="c"></canvas>');

    let base64 = await w.evaluateJavaScript(
        `

    let colour = "${colour}",
        background = "${background}",
        size = ${size}*3,
        lineWidth = ${barWidth}*3,
        percent = ${value * 100};

    let canvas = document.getElementById('c'),
        c = canvas.getContext('2d');
    canvas.width = size;
    canvas.height = size;
    let posX = canvas.width / 2,
        posY = canvas.height / 2,
        onePercent = 360 / 100,
        result = onePercent * percent;
    c.lineCap = 'round';
    c.beginPath();
    c.arc(posX, posY, (size-lineWidth-1)/2, (Math.PI/180) * 270, (Math.PI/180) * (270 + 360));
    c.strokeStyle = background;
    c.lineWidth = lineWidth;
    c.stroke();
    c.beginPath();
    c.strokeStyle = colour;
    c.lineWidth = lineWidth;
    c.arc(posX, posY, (size-lineWidth-1)/2, (Math.PI/180) * 270, (Math.PI/180) * (270 + result));
    c.stroke();
    completion(canvas.toDataURL().replace("data:image/png;base64,",""));
  `,
        true,
    );

    const image = Image.fromData(Data.fromBase64String(base64));
    let stack = on.addStack();
    stack.size = new Size(size, size);
    stack.backgroundImage = image;
    stack.centerAlignContent();

    let padding = barWidth * 2;
    stack.setPadding(padding, padding, padding, padding);

    return stack;
}
