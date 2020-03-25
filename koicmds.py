import discord
import config
from koimodules import *

# 커맨드 파일
class koicmd():
    async def test(message,self):
        await message.channel.send('테스트!')
        return

    async def stop(message,self):
        if str(message.author.id) == config.ownerid:
            await message.channel.send('종료 시작.')
            await log.write(message,'sutdwn')
            await discord.Client.close(self)
            return
        else:
            await log.write(message, 'cmddnd')
            await message.channel.send('관리자만 끌 수 있습니다.')
            return

    async def myid(message,self):
        await message.channel.send('당신의 ID코드 : ' + str(message.author.id))
        return

    async def 출석(message,self):
        await daily.check(message,self)
        return

    async def prefix(message,self):
        try:
            setprefix = message.content.split(' ')[1]
        except IndexError:
            await prefixer.reset(message)
        else:
            await prefixer.set(message,setprefix)

    async def 비트코인(message,self):
        global excepts
        excepts = False
        try:
            arg1 = message.content.split(' ')[1]
        except IndexError:
            excepts = True

        if excepts or arg1 == '명령어' and arg1 != '구매' and arg1 != '개수' and arg1 != '존버' and arg1 != '판매' and arg1 != '시세':
            prefix = await prefixer.check(message)
            embed = discord.Embed(title="비트코인 명령어", description="비트코인 : 시세 1원 부터 1천만원 까지", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name=prefix + "비트코인 구매 <수량>", value='수량 만큼 삽니다.', inline=True)
            embed.add_field(name=prefix + "비트코인 존버", value='존버해서 시세를 변동합니다.', inline=True)
            embed.add_field(name=prefix + "비트코인 판매 <수량>", value='수량 만큼 팝니다.', inline=True)
            embed.add_field(name=prefix + "비트코인 시세", value='현재 시세를 봅니다.', inline=True)
            embed.add_field(name=prefix + "비트코인 개수", value='현재 가진 수량을 봅니다.', inline=True)
            await message.channel.send(embed=embed)
        else:
            try:
                arg2 = message.content.split(' ')[2]
            except IndexError:
                if arg1 == '존버':
                    await bitcoin.stay(message)
                if arg1 == '시세':
                    await bitcoin.value(message)
                if arg1 == '수량':
                    await bitcoin.amount(message)
                if arg1 == '판매':
                    await message.channel.send('명령어 구문이 올바르지 않습니다.')
                if arg1 == '구매':
                    await message.channel.send('명령어 구문이 올바르지 않습니다.')
            else:
                if arg1 == '판매':
                    await bitcoin.sell(message,arg2)
                if arg1 == '구매':
                    await bitcoin.buy(message,arg2)
                if arg1 == '존버':
                    await bitcoin.stay(message)
                if arg1 == '시세':
                    await bitcoin.value(message)
                if arg1 == '수량':
                    await bitcoin.amount(message)
                    
    async def 코이코인(message,self):
        global excepts
        excepts = False
        try:
            arg1 = message.content.split(' ')[1]
        except IndexError:
            excepts = True

        if excepts or arg1 == '명령어' and arg1 != '구매' and arg1 != '개수' and arg1 != '존버' and arg1 != '판매' and arg1 != '시세':
            prefix = await prefixer.check(message)
            embed = discord.Embed(title="코이코인 명령어", description="코이코인 : 시세 1원 부터 514원 까지", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name=prefix + "코이코인 구매 <수량>", value='수량 만큼 삽니다.', inline=True)
            embed.add_field(name=prefix + "코이코인 존버", value='존버해서 시세를 변동합니다.', inline=True)
            embed.add_field(name=prefix + "코이코인 판매 <수량>", value='수량 만큼 팝니다.', inline=True)
            embed.add_field(name=prefix + "코이코인 시세", value='현재 시세를 봅니다.', inline=True)
            embed.add_field(name=prefix + "코이코인 개수", value='현재 가진 수량을 봅니다.', inline=True)
            await message.channel.send(embed=embed)
        else:
            try:
                arg2 = message.content.split(' ')[2]
            except IndexError:
                if arg1 == '존버':
                    await koicoin.stay(message)
                if arg1 == '시세':
                    await koicoin.value(message)
                if arg1 == '수량':
                    await koicoin.amount(message)
                if arg1 == '판매':
                    await message.channel.send('명령어 구문이 올바르지 않습니다.')
                if arg1 == '구매':
                    await message.channel.send('명령어 구문이 올바르지 않습니다.')
            else:
                if arg1 == '판매':
                    await koicoin.sell(message,arg2)
                if arg1 == '구매':
                    await koicoin.buy(message,arg2)
                if arg1 == '존버':
                    await koicoin.stay(message)
                if arg1 == '시세':
                    await koicoin.value(message)
                if arg1 == '수량':
                    await koicoin.amount(message)
                    
    async def 지갑(message,self):
        embed = discord.Embed(title=message.author.name + "님", description="코이코인, 비트코인, 잔고를 나열합니다.", color=0x62c1cc)
        if await userDB.read(message,'koicoin') != None:
            embed.add_field(name="코이코인", value=await userDB.read(message,'koicoin') + '개', inline=True)
        if await userDB.read(message,'bitcoin') != None:
            embed.add_field(name="비트코인", value=await userDB.read(message,'bitcoin') + '개', inline=True)
        if await userDB.read(message,'money') != None:
            embed.add_field(name="소지금", value=await userDB.read(message,'money') + '원', inline=True)
        embed.add_field(name="아무것도 뜨지 않나요?", value='셋 중 하나 이상을 소지하는지 확인하세요.', inline=True)
        await message.channel.send(embed=embed)
