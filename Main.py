# -*- encoding: utf-8 -*-
import discord
import config
from koicmds import koicmd
from koimodules import log
from koimodules import prefixer

class MyClient(discord.Client):
    async def on_ready(self):
        
        # 로그인 확인
        
        await log.write(self,'slogin')
        game = discord.Game("발전")
        await client.change_presence(status=discord.Status.online, activity=game)

    async def on_message(self, message):
        
        # 봇이 전송한 메시지 로깅
        if message.author == client.user:
            await log.write(message,'sndmsg')
            return

        prefix = await prefixer.check(message)
            
        # 봇 명령어 접미사 감지
        if message.content.startswith(prefix):
            ftext = message.content.replace(prefix,'',1)
            cmd = ftext.split(" ")[0]

            # 커맨드 유효 확인
            if cmd in dir(koicmd):
                await log.write(message,'cmdget')
                await getattr(koicmd, cmd)(message,self)
            else:
                return

token = config.token
client = MyClient()
client.run(token)
