import discord
from koimodules import log

# 커맨드 파일
class koicmd():
    async def test(message,self):
        await log.write(message,'cmdget')
        await message.channel.send('테스트!')
        return

    async def stop(message,self):
        await log.write(message,'cmdget')
        await log.write(message,'sutdwn')
        await discord.Client.close(self)

