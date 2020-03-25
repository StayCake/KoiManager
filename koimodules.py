import os.path
import datetime
import aiofiles
import random
import discord
import config

from os import path

# 각종 클래스 모듈 정의 파일

class date:
    def datefm():
        dt = datetime.datetime.now()
        return dt.strftime("%Y-%m-%d")
    def load():
        dt = datetime.datetime.now()
        return dt.strftime("%Y년 %m월 %d일 | %H시 %M분 %S초 ) ")

class daily:
    async def check(message,self):
            
        if await userDB.read(message,'checkdate') != None and await guildDB.read(message,'checkdate') != None:
            ckdate = await userDB.read(message,'checkdate')
            rankstr = await userDB.read(message,'checkrank')
            if str(ckdate) == date.datefm():
                check = 'did'
            else:
                check = 'tmr'
        elif await userDB.read(message,'checkdate') != None and await guildDB.read(message,'checkdate') == None:
            ckdate = await userDB.read(message,'checkdate')
            rankstr = await userDB.read(message,'checkrank')
            if str(ckdate) == date.datefm():
                check = 'd2d'
            else:
                check = 'ngd'
        elif await userDB.read(message,'checkdate') == None and await guildDB.read(message,'checkdate') != None:
            check = 'nud'
        else:
            check = 'ngd'
            
        global guilddate
        global guildrank
        guilddate = ''
        guildrank = 0
        if check != 'ngd' and check != 'd2d':
            guilddate = await guildDB.read(message, 'checkdate')
            guildrank = int(await guildDB.read(message, 'checkrank'))
        
        if guilddate != date.datefm() or check == 'ngd':
            guilddate = 'oneday'
            guildrank = 0

        global serverdate
        global serverrank
        if await serverDB.read('checkdate') != None:
            serverdate = await serverDB.read('checkdate')
            serverrk = await serverDB.read('checkrank')
            serverrank = int(serverrk)
        else:
            serverrank = 0
            serverdate = 'oneday'
            
        if serverdate != date.datefm():
            serverrank = 0
            
        if check != 'did' and check != 'd2d':
            m = await userDB.read(message, 'money')
            if m != None:
                fmoney = int(m) + 150
            else:
                fmoney = 150
            await userDB.set(message,'checkdate',date.datefm())
            await userDB.set(message,'checkrank',str(guildrank + 1))
            await guildDB.set(message,'checkdate',date.datefm())
            await guildDB.set(message,'checkrank',str(guildrank + 1))
            embed = discord.Embed(title="출첵!", description="출석이 처리 되었습니다.", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name="출석 일자", value=str(date.datefm()), inline=True)
            embed.add_field(name="서버 순위", value=str(guildrank + 1) + '위', inline=True)
            embed.add_field(name="전체 순위", value=str(serverrank + 1) + '위', inline=True)
            if (serverrank + 1) == 1:
                await userDB.set(message,'money',str(fmoney + 100))
                await serverDB.set('checkdate',date.datefm())
                await serverDB.set('checkrank',str(serverrank + 1))
                embed.add_field(name="전체 1위!", value='100원 추가 획득', inline=True)
                embed.add_field(name="총 소지금", value=str(fmoney + 100) + '원', inline=True)
            else:
                await userDB.set(message,'money',str(fmoney))
                await serverDB.set('checkdate',date.datefm())
                await serverDB.set('checkrank',str(serverrank + 1))
                embed.add_field(name="총 소지금", value=str(fmoney), inline=True)
            await message.channel.send(embed=embed)
        else:
            embed = discord.Embed(title="오늘은 여기까지!", description="이미 출석하셨습니다.", color=0x62c1cc)
            embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
            embed.add_field(name="출석 일자", value=str(date.datefm()), inline=True)
            await message.channel.send(embed=embed)
        return

class userDB:
    async def set(message,idx,value):
        if await userDB.read(message,idx) != None:
            datas = await File.get('\\UserDB\\' + str(message.author.id))
            for d in range(1,(datas.count('\n')+1)):
                dtc = datas.split('\n')[d]
                dtf, dtb = dtc.split(':')
                if dtf == idx:
                    datas = datas.replace(dtc,dtf + ':' + value)
            await File.write('\\UserDB\\' + str(message.author.id),datas)
        else:
            await File.append('\\UserDB\\' + str(message.author.id),'\n' + idx + ':' + value)
        return
    async def read(message,idx):   
        if path.exists(os.getcwd() + '\\UserDB\\' + str(message.author.id) + '.kbd'):
            datas = await File.get('\\UserDB\\' + str(message.author.id))
            if idx in datas:
                global end
                end = False
                for d in range(1,(datas.count('\n')+1)):
                    dtc = datas.split('\n')[d]
                    dtf, dtb = dtc.split(':')
                    if dtf == idx:
                        global result
                        result = dtb
                        end = True
                        break
                if end:
                    return result
                else:
                    return None
            else:
                return None
        else:
            return None
        
class guildDB:
    async def set(message,idx,value):
        if await guildDB.read(message,idx) != None:
            datas = await File.get('\\UserDB\\' + str(message.guild.id) + '__guild__')
            for d in range(1,(datas.count('\n')+1)):
                dtc = datas.split('\n')[d]
                dtf, dtb = dtc.split(':')
                if dtf == idx:
                    datas = datas.replace(dtc,dtf + ':' + value)
            await File.write('\\UserDB\\' + str(message.guild.id) + '__guild__',datas)
        else:
            await File.append('\\UserDB\\' + str(message.guild.id) + '__guild__','\n' + idx + ':' + value)
        return
    async def read(message,idx):
        if path.exists(os.getcwd() + '\\UserDB\\' + str(message.guild.id) + '__guild__.kbd'):
            datas = await File.get('\\UserDB\\' + str(message.guild.id) + '__guild__')
            if idx in datas:
                global end
                end = False
                for d in range(1,(datas.count('\n')+1)):
                    dtc = datas.split('\n')[d]
                    dtf, dtb = dtc.split(':')
                    if dtf == idx:
                        global result
                        result = dtb
                        end = True
                        break
                if end:
                    return result
                else:
                    return None
            else:
                return None
        else:
            return None

class serverDB:
    async def set(idx,value):
        if await serverDB.read(idx) != None:
            datas = await File.get('\\UserDB\\__server__')
            for d in range(1,(datas.count('\n')+1)):
                dtc = datas.split('\n')[d]
                dtf, dtb = dtc.split(':')
                if dtf == idx:
                    datas = datas.replace(dtc,dtf + ':' + value)
            await File.write('\\UserDB\\__server__',datas)
        else:
            await File.append('\\UserDB\\__server__','\n' + idx + ':' + value)
        return
    async def read(idx):
        if path.exists(os.getcwd() + '\\UserDB\\__server__.kbd'):
            datas = await File.get('\\UserDB\\__server__')
            if idx in datas:
                global end
                end = False
                for d in range(1,(datas.count('\n')+1)):
                    dtc = datas.split('\n')[d]
                    dtf, dtb = dtc.split(':')
                    if dtf == idx:
                        global result
                        result = dtb
                        end = True
                        break
                if end:
                    return result
                else:
                    return None
            else:
                return None
        else:
            return None
        
class File:
    async def write(file, data):
        async with aiofiles.open(os.getcwd() + file + ".kbd", mode='w', encoding='UTF8') as f:
            await f.write(data)
        return
    async def append(file, data):
        if not path.exists(os.getcwd() + file + ".kbd"):
            await File.write(file, data)
        else:
            async with aiofiles.open(os.getcwd() + file + ".kbd", mode='a', encoding='UTF8') as f:
                await f.write(data)
        return
    async def get(file):
        async with aiofiles.open(os.getcwd() + file + ".kbd", mode='r', encoding='UTF8') as f:
            rtd = await f.read()
        return rtd

class log:
    async def send(data):
        if not path.isdir(os.getcwd() + '\\UserDB\\'):
            os.makedirs(os.getcwd() + '\\UserDB\\')
        if not path.exists(os.getcwd() + "\\UserDB\\log.kbd"):
            await File.write("log",data)
            return
        else:
            await File.append("log",data)
            return
    async def write(msg,md):
        if md == 'cmdget':
            logd = date.load() + '명령어 수신 [' + msg.author.name + '] : ' + msg.content
        elif md == 'slogin':
            logd = date.load() + '로그인 : {0}'.format(msg.user)
        elif md == 'sndmsg':
            logd = date.load() + '메시지 전송 [' + msg.guild.name + '] : ' + msg.content
        elif md == 'sutdwn':
            logd = date.load() + '시스템 종료.'
        elif md == 'cmddnd':
            logd = date.load() + '권한 부족 [' + msg.author.name + '] : ' + msg.content
        print(logd)
        await log.send(logd + "\n")

class bitcoin:
    async def stay(message):
        befvalue = await serverDB.read('bitcoin')
        if befvalue == None:
            befvalue = 0
        nowvalue = random.randrange(1,10000000)
        nowvalstr = str(nowvalue)
        await serverDB.set('bitcoin',nowvalstr)
        if int(befvalue) > nowvalue:
            await message.channel.send(':arrow_heading_down: 비트코인이 하락 했습니다. 현재가 : ' + nowvalstr + '원 :arrow_heading_down:')
        elif int(befvalue) == nowvalue:
            await message.channel.send(':arrow_right: 비트코인이 변화가 없습니다. 현재가 : ' + nowvalstr + '원 :arrow_right:')
        elif int(befvalue) < nowvalue:
            await message.channel.send(':arrow_heading_up: 비트코인이 상승 했습니다. 현재가 : ' + nowvalstr + '원 :arrow_heading_up:')
    async def buy(message,amount):
        value = await serverDB.read('bitcoin')
        if value == None:
            await message.channel.send('비트코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            mymoney = await userDB.read(message,'money')
            total = int(value) * int(amount)
            if mymoney == None:
                await message.channel.send('소지금이 부족합니다.')
            else:
                hmoney = int(mymoney)
                if hmoney < total:
                    await message.channel.send('소지금이 부족합니다.')
                else:
                    finalmoney = hmoney - total
                    await userDB.set(message,'money',str(finalmoney))
                    haveamount = await userDB.read(message,'bitcoin')
                    if haveamount == None:
                        totalamount = amount
                        await userDB.set(message,'bitcoin',amount)
                    else:
                        totalamount = int(haveamount) + int(amount)
                        await userDB.set(message,'bitcoin',str(totalamount))
                    embed = discord.Embed(title="구매 완료", description="비트코인이 구매 되었습니다.", color=0x62c1cc)
                    embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
                    embed.add_field(name="구매 일자", value=str(date.datefm()), inline=True)
                    embed.add_field(name="결재 금액", value=str(total) + '원', inline=True)
                    embed.add_field(name="결재 수량", value=amount + '개', inline=True)
                    embed.add_field(name="현재 잔고", value=str(finalmoney) + '원', inline=True)
                    embed.add_field(name="현재 잔량", value=str(totalamount) + '개', inline=True)
                    await message.channel.send(embed=embed)
    async def sell(message,amount):
        value = await serverDB.read('bitcoin')
        if value == None:
            await message.channel.send('비트코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            mymoney = await userDB.read(message,'money')
            haveamount = await userDB.read(message,'bitcoin')
            if haveamount == None:
                await message.channel.send('소지하신 비트코인이 없습니다. 코이코인 구입 후 판매하세요.')
            else:
                if int(haveamount) < int(amount):
                    await message.channel.send('소지하신 비트코인이 모자릅니다. 코이코인 구입 후 판매하세요.')
                else:
                    hmoney = int(mymoney)
                    total = int(value) * int(amount)
                    finalmoney = hmoney + total
                    await userDB.set(message,'money',str(finalmoney))
                    totalamount = int(haveamount) - int(amount)
                    await userDB.set(message,'bitcoin',str(totalamount))
                    embed = discord.Embed(title="판매 완료", description="비트코인이 판매 되었습니다.", color=0x62c1cc)
                    embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
                    embed.add_field(name="판매 일자", value=str(date.datefm()), inline=True)
                    embed.add_field(name="판매 금액", value=str(total) + '원', inline=True)
                    embed.add_field(name="판매 수량", value=amount + '개', inline=True)
                    embed.add_field(name="현재 잔고", value=str(finalmoney) + '원', inline=True)
                    embed.add_field(name="현재 잔량", value=str(totalamount) + '개', inline=True)
                    await message.channel.send(embed=embed)
    async def value(message):
        value = await serverDB.read('bitcoin')
        if value == None:
            await message.channel.send('비트코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            await message.channel.send('비트코인 현재 시세 : 개당 ' + value + '원')
    async def amount(message):
        haveamount = await userDB.read(message,'bitcoin')
        if haveamount == None:
            await message.channel.send('소지하신 비트코인이 없습니다.')
        else:
            await message.channel.send('비트코인 현재 수량 : ' + haveamount + '개')
            
class koicoin:
    async def stay(message):
        befvalue = await serverDB.read('koicoin')
        if befvalue == None:
            befvalue = 0
        nowvalue = random.randrange(1,514)
        nowvalstr = str(nowvalue)
        await serverDB.set('koicoin',nowvalstr)
        if int(befvalue) > nowvalue:
            await message.channel.send(':arrow_heading_down: 코이코인이 하락 했습니다. 현재가 : ' + nowvalstr + '원 :arrow_heading_down:')
        elif int(befvalue) == nowvalue:
            await message.channel.send(':arrow_right: 코이코인이 변화가 없습니다. 현재가 : ' + nowvalstr + '원 :arrow_right:')
        elif int(befvalue) < nowvalue:
            await message.channel.send(':arrow_heading_up: 코이코인이 상승 했습니다. 현재가 : ' + nowvalstr + '원 :arrow_heading_up:')
    async def buy(message,amount):
        value = await serverDB.read('koicoin')
        if value == None:
            await message.channel.send('코이코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            mymoney = await userDB.read(message,'money')
            total = int(value) * int(amount)
            if mymoney == None:
                await message.channel.send('소지금이 부족합니다.')
            else:
                hmoney = int(mymoney)
                if hmoney < total:
                    await message.channel.send('소지금이 부족합니다.')
                else:
                    finalmoney = hmoney - total
                    await userDB.set(message,'money',str(finalmoney))
                    haveamount = await userDB.read(message,'koicoin')
                    if haveamount == None:
                        totalamount = amount
                        await userDB.set(message,'koicoin',amount)
                    else:
                        totalamount = int(haveamount) + int(amount)
                        await userDB.set(message,'koicoin',str(totalamount))
                    embed = discord.Embed(title="구매 완료", description="코이코인이 구매 되었습니다.", color=0x62c1cc)
                    embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
                    embed.add_field(name="구매 일자", value=str(date.datefm()), inline=True)
                    embed.add_field(name="결재 금액", value=str(total) + '원', inline=True)
                    embed.add_field(name="결재 수량", value=amount + '개', inline=True)
                    embed.add_field(name="현재 잔고", value=str(finalmoney) + '원', inline=True)
                    embed.add_field(name="현재 잔량", value=str(totalamount) + '개', inline=True)
                    await message.channel.send(embed=embed)
    async def sell(message,amount):
        value = await serverDB.read('koicoin')
        if value == None:
            await message.channel.send('코이코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            mymoney = await userDB.read(message,'money')
            haveamount = await userDB.read(message,'koicoin')
            if haveamount == None:
                await message.channel.send('소지하신 코이코인이 없습니다. 코이코인 구입 후 판매하세요.')
            else:
                if int(haveamount) < int(amount):
                    await message.channel.send('소지하신 코이코인이 모자릅니다. 코이코인 구입 후 판매하세요.')
                else:
                    hmoney = int(mymoney)
                    total = int(value) * int(amount)
                    finalmoney = hmoney + total
                    await userDB.set(message,'money',str(finalmoney))
                    totalamount = int(haveamount) - int(amount)
                    await userDB.set(message,'koicoin',str(totalamount))
                    embed = discord.Embed(title="판매 완료", description="코이코인이 판매 되었습니다.", color=0x62c1cc)
                    embed.set_footer(text=message.author.name + "님이 실행함 | 63C 매니저")
                    embed.add_field(name="판매 일자", value=str(date.datefm()), inline=True)
                    embed.add_field(name="판매 금액", value=str(total) + '원', inline=True)
                    embed.add_field(name="판매 수량", value=amount + '개', inline=True)
                    embed.add_field(name="현재 잔고", value=str(finalmoney) + '원', inline=True)
                    embed.add_field(name="현재 잔량", value=str(totalamount) + '개', inline=True)
                    await message.channel.send(embed=embed)
    async def value(message):
        value = await serverDB.read('koicoin')
        if value == None:
            await message.channel.send('코이코인이 생기지 않았습니다. 존버부터 하셔야 합니다.')
        else:
            await message.channel.send('코이코인 현재 시세 : 개당 ' + value + '원')
    async def amount(message):
        haveamount = await userDB.read(message,'koicoin')
        if haveamount == None:
            await message.channel.send('소지하신 코이코인이 없습니다.')
        else:
            await message.channel.send('코이코인 현재 수량 : ' + haveamount + '개')

class prefixer:
    async def check(message):
        if await guildDB.read(message,'prefix') != None:
            return await guildDB.read(message,'prefix')
        else:
            return config.prefix
    async def set(message,value):
        await guildDB.set(message,'prefix',value)
        await message.channel.send('이제 본 서버의 명령 칭호는 `' + setprefix + '`입니다.')
    async def reset(message):
        await guildDB.set(message,'prefix',config.prefix)
        await message.channel.send('본 서버의 명령 칭호가 초기화 되었습니다.')
