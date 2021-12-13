package sample;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

public class Quadtree {
    int x, y, width;
    Quadtree childNW, childSW, childSE, childNE;
    Quadtree parent;
    boolean isLeaf, isRoot;
    int numCells;

    public Quadtree(int x, int y, int width, int layersToCreate, boolean isRoot, Quadtree parent){
        this.x = x;
        this.y = y;
        this.width = width;
        this.isRoot = isRoot;
        this.parent = parent;

        numCells = 0;

        if(layersToCreate > 0){
            this.isLeaf = false;
            childNW = new Quadtree(x,         y,         width/2, layersToCreate-1, false, this);
            childSW = new Quadtree(x,         y+width/2, width/2, layersToCreate-1, false, this);
            childSE = new Quadtree(x+width/2, y+width/2, width/2, layersToCreate-1, false, this);
            childNE = new Quadtree(x+width/2, y,         width/2, layersToCreate-1, false, this);
        }
        else if(layersToCreate == 0){
            this.isLeaf = true;
        }
    }

    public int update(){
        //System.out.println((isRoot?"Root: ":(isLeaf?"Leaf: ":"Branch: ")) + "Width: " + width + " NumCells: " + numCells);

        // updates every leaf node in need
//        if(numCells > 0) {
//            if (isLeaf)
//                Main.stepForQuadTree(x-1, y-1, x + width+1, y + width+1);
//            else {
//                childNW.update();
//                childSW.update();
//                childSE.update();
//                childNE.update();
//            }
//        }
//        return 0;

        if(isLeaf && numCells > 0) return 1;
        else if(isLeaf) return 0;

        int childrenWantingToUpdate = childNW.update()<<3 | childSW.update()<<2 | childSE.update()<<1 | childNE.update();

        if(childrenWantingToUpdate == 0b0000) return 0;
        else if(childrenWantingToUpdate == 0b1111) {
            return 1;
        }
        else {
            if((childrenWantingToUpdate & 0b1000) > 0)
                Main.stepForQuadTree(childNW.x-1, childNW.y-1, childNW.x + childNW.width+1, childNW.y + childNW.width+1);
            if((childrenWantingToUpdate & 0b0100) > 0)
                Main.stepForQuadTree(childSW.x-1, childSW.y-1, childSW.x + childSW.width+1, childSW.y + childSW.width+1);
            if((childrenWantingToUpdate & 0b0010) > 0)
                Main.stepForQuadTree(childSE.x-1, childSE.y-1, childSE.x + childSE.width+1, childSE.y + childSE.width+1);
            if((childrenWantingToUpdate & 0b0001) > 0)
                Main.stepForQuadTree(childNE.x-1, childNE.y-1, childNE.x + childNE.width+1, childNE.y + childNE.width+1);

            return 0;
        }
    }

    public void changeNumCellsFromLeaf(int n){
        numCells += n;
        if(!isRoot) parent.changeNumCellsFromLeaf(n);
    }

    public void readAllFromArray(){
        for (int y = this.y; y < this.y + this.width; y++) {
            for (int x = this.x; x < this.x + this.width; x++) {
                if((Main.cellStatesWrite[y*Main.universeSize + x]&1) == 1) changeNumCellsFromRoot(1, x, y);
            }
        }
    }

    public void changeNumCellsFromRoot(int n, int px, int py){
        if(isRoot && (px < x || py < y || px >= x+width || py >= y+width)){
            return;
        }
        numCells += n;
        if(!isLeaf){
            if(px < x + width/2){
                if(py < y + width/2) childNW.changeNumCellsFromRoot(n, px, py);
                else childSW.changeNumCellsFromRoot(n, px, py);
            }
            else{
                if(py < y + width/2) childNE.changeNumCellsFromRoot(n, px, py);
                else childSE.changeNumCellsFromRoot(n, px, py);
            }
        }
    }

    public void draw(GraphicsContext ctx, double tileSize, double zoom, double translationX, double translationY){

        if(numCells > 0){
            ctx.setFill(new Color(1, 1, 1, 0.1));
            ctx.fillRect(x*tileSize*zoom + translationX, y*tileSize*zoom + translationY, tileSize*zoom*width, tileSize*zoom*width);
        }
        ctx.setLineWidth((double)Math.pow(width, 1.5)*zoom/300);
        ctx.strokeRect(x*tileSize*zoom + translationX, y*tileSize*zoom + translationY, tileSize*zoom*width, tileSize*zoom*width);

        if(!isLeaf) {
            childSW.draw(ctx, tileSize, zoom, translationX, translationY);
            childSE.draw(ctx, tileSize, zoom, translationX, translationY);
            childNW.draw(ctx, tileSize, zoom, translationX, translationY);
            childNE.draw(ctx, tileSize, zoom, translationX, translationY);
        }
    }
}
