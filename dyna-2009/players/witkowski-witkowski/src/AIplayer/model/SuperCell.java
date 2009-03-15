package AIplayer.model;

import org.jdyna.Cell;
import org.jdyna.CellType;

/**
 * Remember more information in one cell of board
 * 
 * @author Lukasz Witkowski
 * 
 */
public class SuperCell extends Cell {

	/** Number of players on cell. */
	private int numPlayers = 0;

	/** Is that cell occupy by a bomb */
	private boolean isBomb = false;

	/** Some more information about bomb on that cell. */
	private BombInfo bombinfo = null;

	/**
	 * 
	 * @param type
	 *            Type of a cell generated by Game controller.
	 */
	public SuperCell(CellType type) {
		super(type);
		this.setBomb(false);
		this.setBombinfo(null);
		this.setNumPlayers(0);
	}

	public SuperCell(CellType type, int numPlayers, boolean isBomb,
			BombInfo bombinfo) {
		super(type);
		this.setNumPlayers(numPlayers);
		this.setBomb(isBomb);
		this.setBombinfo(bombinfo);
	}

	public SuperCell clone() {
		BombInfo bI = null;
		if (this.bombinfo != null) {
			bI = new BombInfo(this.bombinfo.getOwnerName(), this.bombinfo
					.getTimeToBlow(), this.bombinfo.getRange(), this.bombinfo
					.getPosition_x(), this.bombinfo.getPosition_y());
			// bI.idOwnerBoot = this.bombinfo.idOwnerBoot;
		}
		return new SuperCell(this.type, this.numPlayers, this.isBomb, bI);
	}

	public boolean isBomb() {
		return isBomb;
	}

	public BombInfo getBombinfo() {
		return bombinfo;
	}

	public void setBomb(boolean isBomb) {
		this.isBomb = isBomb;
	}

	public void setBombinfo(BombInfo bombinfo) {
		this.bombinfo = bombinfo;
	}

	public void setNumPlayers(int numPlayers) {
		this.numPlayers = numPlayers;
	}

	public int getNumPlayers() {
		return numPlayers;
	}

}