# -*- encoding: utf-8 -*-
import discord
import os.path
import datetime
import aiofiles
import config
from os import path
from discord.ext import commands

bot = commands.Bot(command_prefix='$')

class date:
    def load():
        dt = datetime.datetime.now()
        return dt.strftime("%Y년 %m월 %d일 | %H시 %M분 %S초 ) ")
        
class File:
    async def write(file, data):
        async with aiofiles.open(file + ".kbd", mode='w', encoding='UTF8') as f:
            await f.write(data)
        return
    async def append(file, data):
        async with aiofiles.open(file + ".kbd", mode='a', encoding='UTF8') as f:
            await f.write(data)
        return
    async def get(file):
        async with aiofiles.open(file + ".kbd", mode='r', encoding='UTF8') as f:
            rtd = await f.read()
        return rtd

class log:
    async def send(data):
        if not path.exists("log.kbd"):
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
        print(logd)
        await log.send(logd + "\n")

class MyClient(discord.Client):
    async def on_ready(self):
        # 로그인 확인
        await log.write(self,'slogin')
        game = discord.Game("발전")
        await client.change_presence(status=discord.Status.idle, activity=game)

client = MyClient()

@client.event
async def on_message(self, message):
    # 봇이 전송한 메시지 로깅
    if message.author == client.user:
        await log.write(message,'sndmsg')
        return
    
@bot.command()
async def test(ctx):
    await log.write(message,'cmdget')
    await message.channel.send('Test Complete')
    return

@bot.command()
async def stop(ctx):
    await log.write(message,'cmdget')
    await message.channel.send('종료 시작.')
    await log.write(message,'sutdwn')
    await client.close()
    return

token = config.token
client.run(token)
