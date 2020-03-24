import os.path
import datetime
import aiofiles
import discord

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
        elif await userDB.read(message,'checkdate') == None and await guildDB.read(message,'checkdate') != None:
            check = 'nud'
        else:
            check = 'ngd'
            
        global guilddate
        global guildrank
        guilddate = ''
        guildrank = 0
        if check != 'ngd':
            guilddate = await guildDB.read(message, 'checkdate')
            guildrank = int(await guildDB.read(message, 'checkrank'))
        
        if guilddate != date.datefm() or check == 'ngd':
            guilddate = 'oneday'
            guildrank = 0

        global serverdate
        global serverrank
        global serverdata
        if not path.exists(os.getcwd() + '\\UserDB\\__server__.kbd'):
            serverdata = 'oneday,0'
        else:
            serverdata = await File.get('\\UserDB\\__server__')
        serverdate, serverrk = serverdata.split(',')
        serverrank = int(serverrk)
        if serverdate != date.datefm():
            serverrank = 0
            
        if check != 'did':
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
                await File.write('\\UserDB\\__server__',date.datefm() + ',' + str(serverrank + 1))
                embed.add_field(name="전체 1위!", value='100원 추가 획득', inline=True)
                embed.add_field(name="총 소지금", value=str(fmoney + 100) + '원', inline=True)
            else:
                await userDB.set(message,'money',str(fmoney))
                await File.write('\\UserDB\\__server__',date.datefm() + ',' + str(serverrank + 1))
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
                    datas.replace(dtc,dtb + ':' + value)
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
                    datas.replace(dtc,dtb + ':' + value)
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
            logd = date.load() + '메시지 전송 : ' + msg.content
        elif md == 'sutdwn':
            logd = date.load() + '시스템 종료.'
        elif md == 'cmddnd':
            logd = date.load() + '권한 부족 [' + msg.author.name + '] : ' + msg.content
        print(logd)
        await log.send(logd + "\n")
