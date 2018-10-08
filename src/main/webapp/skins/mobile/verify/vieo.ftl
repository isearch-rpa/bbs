
<html>
    <body>
        <div id="youkuplayer"style="height:370px"></div>
        <script type="text/javascript" src="//player.youku.com/jsapi"></script>

		<script type="text/javascript">
		
			var width= window.screen.availWidth;
			document.getElementById("youkuplayer").style.width= width-30 +"px";
		
			var vieoId='${vieoId}';
			var player = new YKU.Player('youkuplayer',{
			
				styleid: '0',
				
				client_id: '48bdfa0ee74f5930',
				
				vid: vieoId,
				
				newPlayer: true
			});
		
		</script>
    </body>
</html>
