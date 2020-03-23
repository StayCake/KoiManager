import discord
import config
from koimodules import log

# 커맨드 파일
class koicmd():
    async def test(message,self):
        await log.write(message,'cmdget')
        await message.channel.send('테스트!')
        return

    async def stop(message,self):
        await log.write(message,'cmdget')
        if str(message.author.id) == config.ownerid:
            await message.channel.send('종료 시작.')
            await log.write(message,'sutdwn')
            await discord.Client.close(self)
            return
        else:
            await log.write(message, 'cmddnd')
            await message.channel.send('관리자만 끌 수 있습니다.')

    async def myid(message,self):
        await log.write(message,'cmdget')
        await message.channel.send('당신의 ID코드 : ' + str(message.author.id))
        return
